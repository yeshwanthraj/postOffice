package com.postoffice.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.postoffice.app.data.EnvironmentStore
import com.postoffice.app.data.HistoryStore
import com.postoffice.app.model.ApiRequest
import com.postoffice.app.model.Environment
import com.postoffice.app.model.EnvironmentState
import com.postoffice.app.model.KeyValue
import com.postoffice.app.model.ResponseState
import com.postoffice.app.model.resolve
import com.postoffice.app.model.toVarMap
import com.postoffice.app.net.HttpEngine
import com.postoffice.app.net.RequestBuildException
import com.postoffice.app.script.LogLevel
import com.postoffice.app.script.LogLine
import com.postoffice.app.script.ScriptInput
import com.postoffice.app.script.ScriptPhase
import com.postoffice.app.script.ScriptRequest
import com.postoffice.app.script.ScriptResponse
import com.postoffice.app.script.ScriptRunner
import com.postoffice.app.script.TestResult
import com.postoffice.app.script.VarOp
import com.postoffice.app.script.VarScopeName
import com.postoffice.app.script.applyOps
import com.postoffice.app.script.toScriptRequest
import com.postoffice.app.script.toScriptResponse
import com.postoffice.app.script.withScriptRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException

/** Test results and console output from the last Send. */
data class RunReport(
    val tests: List<TestResult> = emptyList(),
    val logs: List<LogLine> = emptyList(),
)

data class UiState(
    val request: ApiRequest = ApiRequest(),
    val response: ResponseState = ResponseState.Idle,
    val report: RunReport = RunReport(),
    val history: List<ApiRequest> = emptyList(),
    val envs: EnvironmentState = EnvironmentState(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = HttpEngine()
    private val scripts = ScriptRunner()
    private val historyStore = HistoryStore(File(app.filesDir, "history.json"))
    private val envStore = EnvironmentStore(File(app.filesDir, "environments.json"))

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var inFlight: Job? = null

    /** Single thread so file writes land in order. */
    private val diskWriter = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    init {
        viewModelScope.launch {
            val (history, envs) = withContext(Dispatchers.IO) { historyStore.load() to envStore.load() }
            _state.update { it.copy(history = history, envs = envs) }
        }
    }

    // ---------------- Request editing ----------------

    fun updateRequest(transform: (ApiRequest) -> ApiRequest) {
        _state.update { it.copy(request = transform(it.request)) }
    }

    fun newRequest() {
        inFlight?.cancel()
        _state.update { it.copy(request = ApiRequest(), response = ResponseState.Idle, report = RunReport()) }
    }

    fun loadFromHistory(item: ApiRequest) {
        _state.update {
            it.copy(request = item.copy(id = UUID.randomUUID().toString()), response = ResponseState.Idle, report = RunReport())
        }
    }

    // ---------------- Sending ----------------

    /**
     * Pre-request script → substitute {{variables}} → send → post-response script.
     * History keeps the request as written (with {{variables}} and scripts), not the resolved one.
     */
    fun send() {
        val original = _state.value.request
        inFlight?.cancel()
        _state.update { it.copy(response = ResponseState.Loading, report = RunReport()) }
        inFlight = viewModelScope.launch {
            val logs = mutableListOf<LogLine>()
            val tests = mutableListOf<TestResult>()
            fun publish(response: ResponseState) =
                _state.update { it.copy(response = response, report = RunReport(tests.toList(), logs.toList())) }

            var started = System.currentTimeMillis()
            try {
                var req = original
                var locals = emptyMap<String, String>()

                if (original.preRequestScript.isNotBlank()) {
                    val out = scripts.run(original.preRequestScript, scriptInput(ScriptPhase.PRE, req.toScriptRequest(), null, locals))
                    logs += out.logs
                    tests += out.tests
                    if (out.error != null) {
                        logs += LogLine(ScriptPhase.PRE, LogLevel.ERROR, out.error)
                        publish(ResponseState.Error("The pre-request script failed, so the request wasn't sent. See Console.", 0))
                        return@launch
                    }
                    applyVarOps(out.ops)
                    locals = out.locals
                    out.request?.let { req = req.withScriptRequest(it) }
                }

                val resolved = req.resolve(_state.value.envs.scope(locals))
                started = System.currentTimeMillis()
                val response = withContext(Dispatchers.IO) { engine.execute(resolved) }
                publish(ResponseState.Success(response))
                addToHistory(original)

                if (original.postResponseScript.isNotBlank()) {
                    val out = scripts.run(
                        original.postResponseScript,
                        scriptInput(ScriptPhase.POST, resolved.toScriptRequest(), response.toScriptResponse(), locals),
                    )
                    logs += out.logs
                    tests += out.tests
                    if (out.error != null) {
                        logs += LogLine(ScriptPhase.POST, LogLevel.ERROR, "Post-response script failed: ${out.error}")
                    } else {
                        applyVarOps(out.ops)
                    }
                    publish(ResponseState.Success(response))
                }
            } catch (e: CancellationException) {
                publish(ResponseState.Error("Request cancelled", System.currentTimeMillis() - started))
                throw e
            } catch (e: RequestBuildException) {
                publish(ResponseState.Error(e.message ?: "Invalid request", 0))
            } catch (e: Exception) {
                publish(ResponseState.Error("${e::class.simpleName}: ${e.message ?: "request failed"}", System.currentTimeMillis() - started))
                addToHistory(original)
            }
        }
    }

    fun cancel() {
        inFlight?.cancel()
    }

    private fun scriptInput(phase: ScriptPhase, request: ScriptRequest, response: ScriptResponse?, locals: Map<String, String>): ScriptInput {
        val envs = _state.value.envs
        return ScriptInput(
            phase = phase,
            request = request,
            response = response,
            environmentName = envs.active?.name,
            environment = envs.active?.variables.toVarMap(),
            globals = envs.globals.toVarMap(),
            locals = locals,
        )
    }

    private fun applyVarOps(ops: List<VarOp>) {
        if (ops.isEmpty()) return
        val envOps = ops.filter { it.scope == VarScopeName.ENVIRONMENT }
        val globalOps = ops.filter { it.scope == VarScopeName.GLOBALS }
        updateEnvs { s ->
            s.copy(
                globals = s.globals.applyOps(globalOps),
                environments = s.environments.map { e ->
                    if (e.id == s.activeId) e.copy(variables = e.variables.applyOps(envOps)) else e
                },
            )
        }
    }

    // ---------------- Environments ----------------

    fun setActiveEnvironment(id: String?) = updateEnvs { it.copy(activeId = id) }

    /** Returns the new environment's id so the manager can select it. */
    fun createEnvironment(): String {
        val names = _state.value.envs.environments.map { it.name }.toSet()
        val name = generateSequence(1) { it + 1 }.map { if (it == 1) "New environment" else "New environment $it" }.first { it !in names }
        val env = Environment(name)
        updateEnvs { it.copy(environments = it.environments + env) }
        return env.id
    }

    fun renameEnvironment(id: String, name: String) = updateEnvs { s ->
        s.copy(environments = s.environments.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun duplicateEnvironment(id: String): String? {
        val source = _state.value.envs.environments.firstOrNull { it.id == id } ?: return null
        val copy = Environment(
            name = "${source.name} copy",
            variables = source.variables.map { KeyValue(it.key, it.value, it.enabled, it.secret) },
        )
        updateEnvs { s ->
            val i = s.environments.indexOfFirst { it.id == id }
            s.copy(environments = s.environments.toMutableList().apply { add(i + 1, copy) })
        }
        return copy.id
    }

    fun deleteEnvironment(id: String) = updateEnvs { s ->
        s.copy(environments = s.environments.filterNot { it.id == id }, activeId = s.activeId.takeUnless { it == id })
    }

    /** [environmentId] null means Globals. */
    fun updateVariables(environmentId: String?, rows: List<KeyValue>) = updateEnvs { s ->
        if (environmentId == null) s.copy(globals = rows)
        else s.copy(environments = s.environments.map { if (it.id == environmentId) it.copy(variables = rows) else it })
    }

    private fun updateEnvs(transform: (EnvironmentState) -> EnvironmentState) {
        _state.update { it.copy(envs = transform(it.envs)) }
        val snapshot = _state.value.envs
        viewModelScope.launch(diskWriter) { runCatching { envStore.save(snapshot) } }
    }

    // ---------------- History ----------------

    fun deleteHistory(item: ApiRequest) = persistHistory(_state.value.history.filterNot { it.id == item.id })

    fun clearHistory() = persistHistory(emptyList())

    private fun addToHistory(req: ApiRequest) {
        val entry = req.copy(id = UUID.randomUUID().toString(), timestamp = System.currentTimeMillis())
        persistHistory((listOf(entry) + _state.value.history).take(MAX_HISTORY))
    }

    private fun persistHistory(list: List<ApiRequest>) {
        _state.update { it.copy(history = list) }
        viewModelScope.launch(diskWriter) { runCatching { historyStore.save(list) } }
    }

    override fun onCleared() {
        diskWriter.close()
    }

    companion object {
        private const val MAX_HISTORY = 100
    }
}
