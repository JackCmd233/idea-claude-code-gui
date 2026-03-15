package com.github.claudecodegui;

import com.github.claudecodegui.model.ConflictStrategy;
import com.github.claudecodegui.model.DeleteResult;
import com.github.claudecodegui.model.PromptScope;
import com.github.claudecodegui.settings.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * Codemoss configuration service (Facade pattern).
 * Delegates specific functionality to specialized managers.
 */
public class CodemossSettingsService {

    private static final Logger LOG = Logger.getInstance(CodemossSettingsService.class);
    private static final int CONFIG_VERSION = 2;
    private static final String CODEX_SANDBOX_MODE_WORKSPACE_WRITE = "workspace-write";
    private static final String CODEX_SANDBOX_MODE_DANGER_FULL_ACCESS = "danger-full-access";
    private static final String STREAMING_ENABLED_KEY = "streamingEnabled";
    private static final String AUTO_OPEN_FILE_ENABLED_KEY = "autoOpenFileEnabled";
    private static final String CUSTOM_WORKING_DIRECTORY_KEY = "customWorkingDirectory";
    private static final String CODEX_SANDBOX_MODE_KEY = "codexSandboxMode";
    private static final String COMMIT_PROMPT_KEY = "commitPrompt";
    private static final String PROJECT_MCP_SERVERS_KEY = "mcpServers";
    private static final String PROJECT_CODEX_MCP_SERVERS_KEY = "codexMcpServers";

    private final Gson gson;

    // Managers
    private final ConfigPathManager pathManager;
    private final ProjectConfigManager projectConfigManager;
    private final ClaudeSettingsManager claudeSettingsManager;
    private final CodexSettingsManager codexSettingsManager;
    private final CodexMcpServerManager codexMcpServerManager;
    private final WorkingDirectoryManager workingDirectoryManager;
    private final AgentManager agentManager;
    private final SkillManager skillManager;
    private final McpServerManager mcpServerManager;
    private final ProviderManager providerManager;
    private final CodexProviderManager codexProviderManager;

    public CodemossSettingsService() {
        this.gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

        // Initialize ConfigPathManager
        this.pathManager = new ConfigPathManager();
        this.projectConfigManager = new ProjectConfigManager(gson, pathManager);

        // Initialize ClaudeSettingsManager
        this.claudeSettingsManager = new ClaudeSettingsManager(gson, pathManager);

        // Initialize WorkingDirectoryManager
        this.workingDirectoryManager = new WorkingDirectoryManager(
                (ignored) -> {
                    try {
                        return readConfig();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                (config) -> {
                    try {
                        writeConfig(config);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        // Initialize AgentManager
        this.agentManager = new AgentManager(gson, pathManager);

        // Initialize SkillManager
        this.skillManager = new SkillManager(
                (ignored) -> {
                    try {
                        return readConfig();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                (config) -> {
                    try {
                        writeConfig(config);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                claudeSettingsManager
        );

        // Initialize McpServerManager
        this.mcpServerManager = new McpServerManager(
                gson,
                (ignored) -> {
                    try {
                        return readConfig();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                (config) -> {
                    try {
                        writeConfig(config);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                claudeSettingsManager
        );

        // Initialize ProviderManager
        this.providerManager = new ProviderManager(
                gson,
                (ignored) -> {
                    try {
                        return readConfig();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                (config) -> {
                    try {
                        writeConfig(config);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                pathManager,
                claudeSettingsManager
        );

        // Initialize CodexSettingsManager
        this.codexSettingsManager = new CodexSettingsManager(gson);

        // Initialize CodexMcpServerManager
        this.codexMcpServerManager = new CodexMcpServerManager(codexSettingsManager);

        // Initialize CodexProviderManager
        this.codexProviderManager = new CodexProviderManager(
                gson,
                (ignored) -> {
                    try {
                        return readConfig();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                (config) -> {
                    try {
                        writeConfig(config);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                pathManager,
                codexSettingsManager
        );
    }

    // ==================== Basic Config Management ====================

    /**
     * Get config file path (~/.codemoss/config.json).
     */
    public String getConfigPath() {
        return pathManager.getConfigPath();
    }

    /**
     * Read the config file.
     */
    public JsonObject readConfig() throws IOException {
        String configPath = getConfigPath();
        File configFile = new File(configPath);

        if (!configFile.exists()) {
            LOG.info("[CodemossSettings] Config file not found, creating default: " + configPath);
            return createDefaultConfig();
        }

        try (FileReader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
            JsonObject config = JsonParser.parseReader(reader).getAsJsonObject();
            LOG.info("[CodemossSettings] Successfully read config from: " + configPath);
            return config;
        } catch (Exception e) {
            LOG.warn("[CodemossSettings] Failed to read config: " + e.getMessage());
            return createDefaultConfig();
        }
    }

    /**
     * Write the config file.
     */
    public void writeConfig(JsonObject config) throws IOException {
        pathManager.ensureConfigDirectory();

        // Back up existing config
        backupConfig();

        String configPath = getConfigPath();
        try (FileWriter writer = new FileWriter(configPath, StandardCharsets.UTF_8)) {
            gson.toJson(config, writer);
            LOG.info("[CodemossSettings] Successfully wrote config to: " + configPath);
        } catch (Exception e) {
            LOG.warn("[CodemossSettings] Failed to write config: " + e.getMessage());
            throw e;
        }
    }

    private void backupConfig() {
        try {
            Path configPath = pathManager.getConfigFilePath();
            if (Files.exists(configPath)) {
                Files.copy(configPath, Paths.get(pathManager.getBackupPath()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOG.warn("[CodemossSettings] Failed to backup config: " + e.getMessage());
        }
    }

    /**
     * Create default config.
     */
    private JsonObject createDefaultConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("version", CONFIG_VERSION);

        // Claude config - empty provider list
        JsonObject claude = new JsonObject();
        JsonObject providers = new JsonObject();

        claude.addProperty("current", ProviderManager.LOCAL_SETTINGS_PROVIDER_ID);
        claude.add("providers", providers);
        config.add("claude", claude);

        return config;
    }

    // ==================== Claude Settings Management ====================

    public JsonObject getCurrentClaudeConfig() throws IOException {
        JsonObject currentConfig = claudeSettingsManager.getCurrentClaudeConfig();

        // If codemossProviderId exists, try to get provider name from codemoss config
        if (currentConfig.has("providerId")) {
            String providerId = currentConfig.get("providerId").getAsString();
            try {
                JsonObject config = readConfig();
                if (config.has("claude")) {
                    JsonObject claude = config.getAsJsonObject("claude");
                    if (claude.has("providers")) {
                        JsonObject providers = claude.getAsJsonObject("providers");
                        if (providers.has(providerId)) {
                            JsonObject provider = providers.getAsJsonObject(providerId);
                            if (provider.has("name")) {
                                currentConfig.addProperty("providerName", provider.get("name").getAsString());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore error - provider name is optional
            }
        }

        return currentConfig;
    }

    public Boolean getAlwaysThinkingEnabledFromClaudeSettings() throws IOException {
        return claudeSettingsManager.getAlwaysThinkingEnabled();
    }

    public void setAlwaysThinkingEnabledInClaudeSettings(boolean enabled) throws IOException {
        claudeSettingsManager.setAlwaysThinkingEnabled(enabled);
    }

    public boolean setAlwaysThinkingEnabledInActiveProvider(boolean enabled) throws IOException {
        return providerManager.setAlwaysThinkingEnabledInActiveProvider(enabled);
    }

    public void applyProviderToClaudeSettings(JsonObject provider) throws IOException {
        claudeSettingsManager.applyProviderToClaudeSettings(provider);
    }

    public void applyActiveProviderToClaudeSettings() throws IOException {
        providerManager.applyActiveProviderToClaudeSettings();
    }

    // ==================== Working Directory Management ====================

    public String getCustomWorkingDirectory(String projectPath) throws IOException {
        if (!hasProjectPath(projectPath)) {
            return readLegacyWorkingDirectory(readConfig(), null);
        }

        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        if (projectConfig.has(CUSTOM_WORKING_DIRECTORY_KEY) && !projectConfig.get(CUSTOM_WORKING_DIRECTORY_KEY).isJsonNull()) {
            return projectConfig.get(CUSTOM_WORKING_DIRECTORY_KEY).getAsString();
        }

        return null;
    }

    public void setCustomWorkingDirectory(String projectPath, String customWorkingDir) throws IOException {
        requireProjectPath(projectPath);
        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);

        if (customWorkingDir == null || customWorkingDir.trim().isEmpty()) {
            projectConfig.remove(CUSTOM_WORKING_DIRECTORY_KEY);
        } else {
            projectConfig.addProperty(CUSTOM_WORKING_DIRECTORY_KEY, customWorkingDir.trim());
        }

        projectConfigManager.writeProjectConfig(projectPath, projectConfig);
    }

    public Map<String, String> getAllWorkingDirectories() throws IOException {
        return workingDirectoryManager.getAllWorkingDirectories();
    }

    public String loadCustomWorkingDirectoryFromGlobal(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        JsonObject projectConfig = projectConfigManager.overwriteProjectBasicsFromGlobal(projectPath, readConfig());
        return projectConfig.has(CUSTOM_WORKING_DIRECTORY_KEY) && !projectConfig.get(CUSTOM_WORKING_DIRECTORY_KEY).isJsonNull()
                ? projectConfig.get(CUSTOM_WORKING_DIRECTORY_KEY).getAsString()
                : null;
    }

    public void saveCustomWorkingDirectoryToGlobal(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        JsonObject config = readConfig();

        JsonObject workingDirectories = ensureObject(config, "workingDirectories");
        if (projectConfig.has(CUSTOM_WORKING_DIRECTORY_KEY) && !projectConfig.get(CUSTOM_WORKING_DIRECTORY_KEY).isJsonNull()) {
            workingDirectories.addProperty("default", projectConfig.get(CUSTOM_WORKING_DIRECTORY_KEY).getAsString());
        } else {
            workingDirectories.remove("default");
        }

        writeConfig(config);
    }

    public boolean isProjectConfigInitialized(String projectPath) {
        return hasProjectPath(projectPath) && projectConfigManager.isProjectConfigInitialized(projectPath);
    }

    public JsonObject ensureProjectConfigInitialized(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        return projectConfigManager.ensureProjectConfigInitialized(projectPath, readConfig());
    }

    // ==================== Commit Prompt Config Management ====================

    /**
     * Get the commit AI prompt.
     *
     * @return commit prompt
     */
    public String getCommitPrompt() throws IOException {
        return getCommitPrompt(null);
    }

    /**
     * 获取项目级 Commit Prompt。
     *
     * @param projectPath 项目路径
     * @return Commit Prompt
     * @throws IOException 配置读取失败
     */
    public String getCommitPrompt(String projectPath) throws IOException {
        if (hasProjectPath(projectPath)) {
            JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
            if (projectConfig.has(COMMIT_PROMPT_KEY) && !projectConfig.get(COMMIT_PROMPT_KEY).isJsonNull()) {
                return projectConfig.get(COMMIT_PROMPT_KEY).getAsString();
            }
        }

        JsonObject config = readConfig();

        if (config.has(COMMIT_PROMPT_KEY)) {
            return config.get(COMMIT_PROMPT_KEY).getAsString();
        }

        return ClaudeCodeGuiBundle.message("commit.defaultPrompt");
    }

    /**
     * Set the commit AI prompt.
     *
     * @param prompt commit prompt
     */
    public void setCommitPrompt(String prompt) throws IOException {
        JsonObject config = readConfig();
        config.addProperty(COMMIT_PROMPT_KEY, prompt);
        writeConfig(config);
        LOG.info("[CodemossSettings] Set global commit prompt");
    }

    /**
     * 保存项目级 Commit Prompt。
     *
     * @param projectPath 项目路径
     * @param prompt commit prompt
     * @throws IOException 配置写入失败
     */
    public void setCommitPrompt(String projectPath, String prompt) throws IOException {
        requireProjectPath(projectPath);
        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        projectConfig.addProperty(COMMIT_PROMPT_KEY, prompt);
        projectConfigManager.writeProjectConfig(projectPath, projectConfig);
        LOG.info("[CodemossSettings] Set project commit prompt for: " + projectPath);
    }

    /**
     * 从全局配置覆盖项目 Commit Prompt。
     *
     * @param projectPath 项目路径
     * @return 覆盖后的值
     * @throws IOException 配置读写失败
     */
    public String loadCommitPromptFromGlobal(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        JsonObject projectConfig = projectConfigManager.overwriteProjectBasicsFromGlobal(projectPath, readConfig());
        if (projectConfig.has(COMMIT_PROMPT_KEY) && !projectConfig.get(COMMIT_PROMPT_KEY).isJsonNull()) {
            return projectConfig.get(COMMIT_PROMPT_KEY).getAsString();
        }
        return ClaudeCodeGuiBundle.message("commit.defaultPrompt");
    }

    /**
     * 将项目 Commit Prompt 提升为全局默认值。
     *
     * @param projectPath 项目路径
     * @throws IOException 配置写入失败
     */
    public void saveCommitPromptToGlobal(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        JsonObject config = readConfig();
        String prompt = projectConfig.has(COMMIT_PROMPT_KEY) && !projectConfig.get(COMMIT_PROMPT_KEY).isJsonNull()
                ? projectConfig.get(COMMIT_PROMPT_KEY).getAsString()
                : ClaudeCodeGuiBundle.message("commit.defaultPrompt");
        config.addProperty(COMMIT_PROMPT_KEY, prompt);
        writeConfig(config);
        LOG.info("[CodemossSettings] Saved project commit prompt to global for: " + projectPath);
    }

    // ==================== Streaming Config Management ====================

    /**
     * Get streaming configuration.
     *
     * @param projectPath project path
     * @return whether streaming is enabled
     */
    public boolean getStreamingEnabled(String projectPath) throws IOException {
        if (!hasProjectPath(projectPath)) {
            return readLegacyBooleanDefault(readConfig(), "streaming", true);
        }

        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        if (projectConfig.has(STREAMING_ENABLED_KEY) && !projectConfig.get(STREAMING_ENABLED_KEY).isJsonNull()) {
            return projectConfig.get(STREAMING_ENABLED_KEY).getAsBoolean();
        }

        return readLegacyBooleanDefault(readConfig(), "streaming", true);
    }

    /**
     * Set streaming configuration.
     *
     * @param projectPath project path
     * @param enabled     whether to enable
     */
    public void setStreamingEnabled(String projectPath, boolean enabled) throws IOException {
        requireProjectPath(projectPath);
        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        projectConfig.addProperty(STREAMING_ENABLED_KEY, enabled);
        projectConfigManager.writeProjectConfig(projectPath, projectConfig);
        LOG.info("[CodemossSettings] Set streaming enabled to " + enabled + " for project: " + projectPath);
    }

    public boolean loadStreamingEnabledFromGlobal(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        JsonObject projectConfig = projectConfigManager.overwriteProjectBasicsFromGlobal(projectPath, readConfig());
        return projectConfig.has(STREAMING_ENABLED_KEY)
                ? projectConfig.get(STREAMING_ENABLED_KEY).getAsBoolean()
                : true;
    }

    public void saveStreamingEnabledToGlobal(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        boolean enabled = projectConfig.has(STREAMING_ENABLED_KEY)
                ? projectConfig.get(STREAMING_ENABLED_KEY).getAsBoolean()
                : true;

        JsonObject config = readConfig();
        upsertLegacyBooleanDefault(config, "streaming", enabled);
        writeConfig(config);
    }

    // ==================== Auto Open File Config Management ====================

    /**
     * Get auto-open file configuration.
     *
     * @param projectPath project path
     * @return whether auto-open file is enabled
     */
    public boolean getAutoOpenFileEnabled(String projectPath) throws IOException {
        if (!hasProjectPath(projectPath)) {
            return readLegacyBooleanDefault(readConfig(), "autoOpenFile", true);
        }

        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        if (projectConfig.has(AUTO_OPEN_FILE_ENABLED_KEY) && !projectConfig.get(AUTO_OPEN_FILE_ENABLED_KEY).isJsonNull()) {
            return projectConfig.get(AUTO_OPEN_FILE_ENABLED_KEY).getAsBoolean();
        }

        return readLegacyBooleanDefault(readConfig(), "autoOpenFile", true);
    }

    /**
     * Set auto-open file configuration.
     *
     * @param projectPath project path
     * @param enabled     whether to enable
     */
    public void setAutoOpenFileEnabled(String projectPath, boolean enabled) throws IOException {
        requireProjectPath(projectPath);
        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        projectConfig.addProperty(AUTO_OPEN_FILE_ENABLED_KEY, enabled);
        projectConfigManager.writeProjectConfig(projectPath, projectConfig);
        LOG.info("[CodemossSettings] Set auto open file enabled to " + enabled + " for project: " + projectPath);
    }

    public boolean loadAutoOpenFileEnabledFromGlobal(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        JsonObject projectConfig = projectConfigManager.overwriteProjectBasicsFromGlobal(projectPath, readConfig());
        return projectConfig.has(AUTO_OPEN_FILE_ENABLED_KEY)
                ? projectConfig.get(AUTO_OPEN_FILE_ENABLED_KEY).getAsBoolean()
                : true;
    }

    public void saveAutoOpenFileEnabledToGlobal(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        boolean enabled = projectConfig.has(AUTO_OPEN_FILE_ENABLED_KEY)
                ? projectConfig.get(AUTO_OPEN_FILE_ENABLED_KEY).getAsBoolean()
                : true;

        JsonObject config = readConfig();
        upsertLegacyBooleanDefault(config, "autoOpenFile", enabled);
        writeConfig(config);
    }

    // ==================== Codex Sandbox Mode Config Management ====================

    /**
     * Get Codex sandbox mode configuration.
     *
     * @param projectPath project path
     * @return sandbox mode (workspace-write or danger-full-access)
     */
    public String getCodexSandboxMode(String projectPath) throws IOException {
        if (!hasProjectPath(projectPath)) {
            return readLegacyStringDefault(readConfig(), CODEX_SANDBOX_MODE_KEY, getDefaultCodexSandboxMode());
        }

        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        if (projectConfig.has(CODEX_SANDBOX_MODE_KEY) && !projectConfig.get(CODEX_SANDBOX_MODE_KEY).isJsonNull()) {
            String mode = projectConfig.get(CODEX_SANDBOX_MODE_KEY).getAsString();
            return isValidCodexSandboxMode(mode) ? mode : getDefaultCodexSandboxMode();
        }

        String globalMode = readLegacyStringDefault(readConfig(), CODEX_SANDBOX_MODE_KEY, getDefaultCodexSandboxMode());
        return isValidCodexSandboxMode(globalMode) ? globalMode : getDefaultCodexSandboxMode();
    }

    /**
     * Set Codex sandbox mode configuration.
     *
     * @param projectPath project path
     * @param sandboxMode sandbox mode (workspace-write or danger-full-access)
     */
    public void setCodexSandboxMode(String projectPath, String sandboxMode) throws IOException {
        if (!isValidCodexSandboxMode(sandboxMode)) {
            throw new IllegalArgumentException("Invalid Codex sandbox mode: " + sandboxMode);
        }

        requireProjectPath(projectPath);
        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        projectConfig.addProperty(CODEX_SANDBOX_MODE_KEY, sandboxMode);
        projectConfigManager.writeProjectConfig(projectPath, projectConfig);
        LOG.info("[CodemossSettings] Set Codex sandbox mode to " + sandboxMode + " for project: " + projectPath);
    }

    public String loadCodexSandboxModeFromGlobal(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        JsonObject projectConfig = projectConfigManager.overwriteProjectBasicsFromGlobal(projectPath, readConfig());
        String sandboxMode = projectConfig.has(CODEX_SANDBOX_MODE_KEY)
                ? projectConfig.get(CODEX_SANDBOX_MODE_KEY).getAsString()
                : getDefaultCodexSandboxMode();
        return isValidCodexSandboxMode(sandboxMode) ? sandboxMode : getDefaultCodexSandboxMode();
    }

    public void saveCodexSandboxModeToGlobal(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        String sandboxMode = getCodexSandboxMode(projectPath);
        JsonObject config = readConfig();
        upsertLegacyStringDefault(config, CODEX_SANDBOX_MODE_KEY, sandboxMode);
        writeConfig(config);
    }

    private boolean isValidCodexSandboxMode(String mode) {
        return CODEX_SANDBOX_MODE_WORKSPACE_WRITE.equals(mode)
                || CODEX_SANDBOX_MODE_DANGER_FULL_ACCESS.equals(mode);
    }

    private String getDefaultCodexSandboxMode() {
        return CODEX_SANDBOX_MODE_WORKSPACE_WRITE;
    }

    private boolean hasProjectPath(String projectPath) {
        return projectPath != null && !projectPath.trim().isEmpty();
    }

    private void requireProjectPath(String projectPath) {
        if (!hasProjectPath(projectPath)) {
            throw new IllegalArgumentException("Project path is required");
        }
    }

    private JsonObject ensureObject(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            JsonObject child = new JsonObject();
            root.add(key, child);
            return child;
        }
        return root.getAsJsonObject(key);
    }

    private boolean readLegacyBooleanDefault(JsonObject config, String key, boolean defaultValue) {
        if (!config.has(key) || !config.get(key).isJsonObject()) {
            return defaultValue;
        }

        JsonObject configObject = config.getAsJsonObject(key);
        if (configObject.has("default") && !configObject.get("default").isJsonNull()) {
            return configObject.get("default").getAsBoolean();
        }
        return defaultValue;
    }

    private String readLegacyStringDefault(JsonObject config, String key, String defaultValue) {
        if (!config.has(key) || !config.get(key).isJsonObject()) {
            return defaultValue;
        }

        JsonObject configObject = config.getAsJsonObject(key);
        if (configObject.has("default") && !configObject.get("default").isJsonNull()) {
            return configObject.get("default").getAsString();
        }
        return defaultValue;
    }

    private String readLegacyWorkingDirectory(JsonObject config, String projectPath) {
        if (!config.has("workingDirectories") || !config.get("workingDirectories").isJsonObject()) {
            return null;
        }

        JsonObject workingDirectories = config.getAsJsonObject("workingDirectories");
        if (hasProjectPath(projectPath)
                && workingDirectories.has(projectPath)
                && !workingDirectories.get(projectPath).isJsonNull()) {
            return workingDirectories.get(projectPath).getAsString();
        }
        if (workingDirectories.has("default") && !workingDirectories.get("default").isJsonNull()) {
            return workingDirectories.get("default").getAsString();
        }
        return null;
    }

    private void upsertLegacyBooleanDefault(JsonObject config, String key, boolean value) {
        JsonObject configObject = ensureObject(config, key);
        configObject.addProperty("default", value);
    }

    private void upsertLegacyStringDefault(JsonObject config, String key, String value) {
        JsonObject configObject = ensureObject(config, key);
        configObject.addProperty("default", value);
    }

    // ==================== Provider Management ====================

    public List<JsonObject> getClaudeProviders() throws IOException {
        return providerManager.getClaudeProviders();
    }

    public JsonObject getActiveClaudeProvider() throws IOException {
        return providerManager.getActiveClaudeProvider();
    }

    public void addClaudeProvider(JsonObject provider) throws IOException {
        providerManager.addClaudeProvider(provider);
    }

    public void saveClaudeProvider(JsonObject provider) throws IOException {
        providerManager.saveClaudeProvider(provider);
    }

    public void updateClaudeProvider(String id, JsonObject updates) throws IOException {
        providerManager.updateClaudeProvider(id, updates);
    }

    public DeleteResult deleteClaudeProvider(String id) {
        return providerManager.deleteClaudeProvider(id);
    }

    @Deprecated
    public void deleteClaudeProviderWithException(String id) throws IOException {
        DeleteResult result = deleteClaudeProvider(id);
        if (!result.isSuccess()) {
            throw new IOException(result.getUserFriendlyMessage());
        }
    }

    public void switchClaudeProvider(String id) throws IOException {
        providerManager.switchClaudeProvider(id);
    }

    public List<JsonObject> parseProvidersFromCcSwitchDb(String dbPath) throws IOException {
        return providerManager.parseProvidersFromCcSwitchDb(dbPath);
    }

    public int saveProviders(List<JsonObject> providers) throws IOException {
        return providerManager.saveProviders(providers);
    }

    public void saveProviderOrder(List<String> orderedIds) throws IOException {
        providerManager.saveProviderOrder(orderedIds);
    }

    public boolean isLocalProviderActive() {
        return providerManager.isLocalProviderActive();
    }

    // ==================== MCP Server Management ====================

    public List<JsonObject> getMcpServers() throws IOException {
        return getGlobalClaudeMcpSource();
    }

    public List<JsonObject> getMcpServersWithProjectPath(String projectPath) throws IOException {
        if (!hasProjectPath(projectPath)) {
            return getGlobalClaudeMcpSource();
        }

        List<JsonObject> globalServers = getGlobalClaudeMcpSource();
        JsonElement projectOverrides = projectConfigManager.getProjectMcpServers(projectPath);
        return mergeProjectMcpServers(globalServers, projectOverrides);
    }

    public void upsertMcpServer(JsonObject server) throws IOException {
        replaceGlobalMcpServer("mcpServers", server);
        mcpServerManager.replaceAllServers(getGlobalClaudeMcpSource(), null);
    }

    public void upsertMcpServer(JsonObject server, String projectPath) throws IOException {
        requireProjectPath(projectPath);
        upsertProjectMcpServer(projectPath, PROJECT_MCP_SERVERS_KEY, server, getMcpServers());
        syncProjectClaudeMcp(projectPath);
    }

    public boolean deleteMcpServer(String serverId) throws IOException {
        boolean deleted = deleteGlobalMcpServer("mcpServers", serverId);
        if (deleted) {
            mcpServerManager.replaceAllServers(getGlobalClaudeMcpSource(), null);
        }
        return deleted;
    }

    public boolean deleteMcpServer(String serverId, String projectPath) throws IOException {
        requireProjectPath(projectPath);
        boolean deleted = deleteProjectMcpServer(projectPath, PROJECT_MCP_SERVERS_KEY, serverId, getMcpServers());
        syncProjectClaudeMcp(projectPath);
        return deleted;
    }

    public void loadProjectMcpServersFromGlobal(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        clearProjectMcpOverrides(projectPath, PROJECT_MCP_SERVERS_KEY);
        syncProjectClaudeMcp(projectPath);
    }

    public void saveProjectMcpServersToGlobal(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        writeGlobalMcpSource("mcpServers", getMcpServersWithProjectPath(projectPath));
        mcpServerManager.replaceAllServers(getGlobalClaudeMcpSource(), projectPath);
    }

    public Map<String, Object> validateMcpServer(JsonObject server) {
        return mcpServerManager.validateMcpServer(server);
    }

    // ==================== Codex MCP Server Management ====================

    public CodexMcpServerManager getCodexMcpServerManager() {
        return codexMcpServerManager;
    }

    public List<JsonObject> getCodexMcpServers() throws IOException {
        return getGlobalCodexMcpSource();
    }

    public List<JsonObject> getCodexMcpServers(String projectPath) throws IOException {
        if (!hasProjectPath(projectPath)) {
            return getGlobalCodexMcpSource();
        }

        List<JsonObject> globalServers = getGlobalCodexMcpSource();
        JsonElement projectOverrides = projectConfigManager.getProjectCodexMcpServers(projectPath);
        return mergeProjectMcpServers(globalServers, projectOverrides);
    }

    public void upsertCodexMcpServer(JsonObject server) throws IOException {
        replaceGlobalMcpServer("codexMcpServers", server);
        codexMcpServerManager.replaceAllServers(getGlobalCodexMcpSource());
    }

    public void upsertCodexMcpServer(JsonObject server, String projectPath) throws IOException {
        requireProjectPath(projectPath);
        upsertProjectMcpServer(projectPath, PROJECT_CODEX_MCP_SERVERS_KEY, server, getCodexMcpServers());
        syncProjectCodexMcp(projectPath);
    }

    public boolean deleteCodexMcpServer(String serverId) throws IOException {
        boolean deleted = deleteGlobalMcpServer("codexMcpServers", serverId);
        if (deleted) {
            codexMcpServerManager.replaceAllServers(getGlobalCodexMcpSource());
        }
        return deleted;
    }

    public boolean deleteCodexMcpServer(String serverId, String projectPath) throws IOException {
        requireProjectPath(projectPath);
        boolean deleted = deleteProjectMcpServer(projectPath, PROJECT_CODEX_MCP_SERVERS_KEY, serverId, getCodexMcpServers());
        syncProjectCodexMcp(projectPath);
        return deleted;
    }

    public void loadProjectCodexMcpServersFromGlobal(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        clearProjectMcpOverrides(projectPath, PROJECT_CODEX_MCP_SERVERS_KEY);
        syncProjectCodexMcp(projectPath);
    }

    public void saveProjectCodexMcpServersToGlobal(String projectPath) throws IOException {
        requireProjectPath(projectPath);
        writeGlobalMcpSource("codexMcpServers", getCodexMcpServers(projectPath));
        codexMcpServerManager.replaceAllServers(getGlobalCodexMcpSource());
    }

    public Map<String, Object> validateCodexMcpServer(JsonObject server) {
        return codexMcpServerManager.validateMcpServer(server);
    }

    private List<JsonObject> mergeProjectMcpServers(List<JsonObject> globalServers, JsonElement projectOverridesElement) {
        Map<String, JsonObject> merged = new java.util.LinkedHashMap<>();
        for (JsonObject globalServer : globalServers) {
            if (!globalServer.has("id")) {
                continue;
            }
            merged.put(globalServer.get("id").getAsString(), globalServer.deepCopy());
        }

        if (projectOverridesElement != null && projectOverridesElement.isJsonArray()) {
            JsonArray overrides = projectOverridesElement.getAsJsonArray();
            for (JsonElement element : overrides) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject override = element.getAsJsonObject();
                if (!override.has("id")) {
                    continue;
                }

                String serverId = override.get("id").getAsString();
                boolean deleted = override.has("deleted") && override.get("deleted").getAsBoolean();
                if (deleted) {
                    merged.remove(serverId);
                    continue;
                }

                JsonObject existing = merged.getOrDefault(serverId, new JsonObject());
                JsonObject mergedServer = existing.deepCopy();
                for (Map.Entry<String, JsonElement> entry : override.entrySet()) {
                    if (!"deleted".equals(entry.getKey())) {
                        mergedServer.add(entry.getKey(), entry.getValue().deepCopy());
                    }
                }
                merged.put(serverId, mergedServer);
            }
        }

        return new java.util.ArrayList<>(merged.values());
    }

    private void upsertProjectMcpServer(
            String projectPath,
            String projectKey,
            JsonObject server,
            List<JsonObject> globalServers
    ) throws IOException {
        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        JsonArray projectOverrides = getOrCreateProjectMcpArray(projectConfig, projectKey);
        String serverId = server.get("id").getAsString();

        boolean replaced = false;
        for (int i = 0; i < projectOverrides.size(); i++) {
            JsonObject existing = projectOverrides.get(i).getAsJsonObject();
            if (existing.has("id") && serverId.equals(existing.get("id").getAsString())) {
                projectOverrides.set(i, server.deepCopy());
                replaced = true;
                break;
            }
        }

        if (!replaced) {
            projectOverrides.add(server.deepCopy());
        }

        // 如果项目中覆盖了全局服务器，移除墓碑标记。
        for (JsonObject globalServer : globalServers) {
            if (globalServer.has("id") && serverId.equals(globalServer.get("id").getAsString())) {
                JsonObject override = server.deepCopy();
                override.remove("deleted");
                replaceProjectMcpEntry(projectOverrides, serverId, override);
                break;
            }
        }

        projectConfigManager.writeProjectConfig(projectPath, projectConfig);
    }

    private boolean deleteProjectMcpServer(
            String projectPath,
            String projectKey,
            String serverId,
            List<JsonObject> globalServers
    ) throws IOException {
        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        JsonArray projectOverrides = getOrCreateProjectMcpArray(projectConfig, projectKey);
        boolean existsInGlobal = globalServers.stream()
                .anyMatch(server -> server.has("id") && serverId.equals(server.get("id").getAsString()));

        boolean removed = false;
        for (int i = projectOverrides.size() - 1; i >= 0; i--) {
            JsonObject existing = projectOverrides.get(i).getAsJsonObject();
            if (existing.has("id") && serverId.equals(existing.get("id").getAsString())) {
                projectOverrides.remove(i);
                removed = true;
            }
        }

        if (existsInGlobal) {
            JsonObject tombstone = new JsonObject();
            tombstone.addProperty("id", serverId);
            tombstone.addProperty("deleted", true);
            projectOverrides.add(tombstone);
            removed = true;
        }

        projectConfigManager.writeProjectConfig(projectPath, projectConfig);
        return removed;
    }

    private void clearProjectMcpOverrides(String projectPath, String projectKey) throws IOException {
        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath);
        projectConfig.remove(projectKey);
        projectConfigManager.writeProjectConfig(projectPath, projectConfig);
    }

    private JsonArray getOrCreateProjectMcpArray(JsonObject projectConfig, String projectKey) {
        if (!projectConfig.has(projectKey) || !projectConfig.get(projectKey).isJsonArray()) {
            JsonArray overrides = new JsonArray();
            projectConfig.add(projectKey, overrides);
            return overrides;
        }
        return projectConfig.getAsJsonArray(projectKey);
    }

    private void replaceProjectMcpEntry(JsonArray projectOverrides, String serverId, JsonObject replacement) {
        for (int i = 0; i < projectOverrides.size(); i++) {
            JsonObject existing = projectOverrides.get(i).getAsJsonObject();
            if (existing.has("id") && serverId.equals(existing.get("id").getAsString())) {
                projectOverrides.set(i, replacement);
                return;
            }
        }
        projectOverrides.add(replacement);
    }

    private void syncProjectClaudeMcp(String projectPath) throws IOException {
        mcpServerManager.replaceAllServers(getMcpServersWithProjectPath(projectPath), projectPath);
    }

    private void syncProjectCodexMcp(String projectPath) throws IOException {
        codexMcpServerManager.replaceAllServers(getCodexMcpServers(projectPath));
    }

    private List<JsonObject> getGlobalClaudeMcpSource() throws IOException {
        return getOrMigrateGlobalMcpSource("mcpServers", () -> mcpServerManager.getMcpServers());
    }

    private List<JsonObject> getGlobalCodexMcpSource() throws IOException {
        return getOrMigrateGlobalMcpSource("codexMcpServers", () -> codexMcpServerManager.getMcpServers());
    }

    private List<JsonObject> getOrMigrateGlobalMcpSource(
            String configKey,
            IOListSupplier fallbackSupplier
    ) throws IOException {
        JsonObject config = readConfig();
        if (config.has(configKey) && config.get(configKey).isJsonArray()) {
            return jsonArrayToObjectList(config.getAsJsonArray(configKey));
        }

        List<JsonObject> fallback = fallbackSupplier.get();
        if (!fallback.isEmpty()) {
            writeGlobalMcpSource(configKey, fallback);
        }
        return fallback;
    }

    private void writeGlobalMcpSource(String configKey, List<JsonObject> servers) throws IOException {
        JsonObject config = readConfig();
        JsonArray array = new JsonArray();
        for (JsonObject server : servers) {
            array.add(server.deepCopy());
        }
        config.add(configKey, array);
        writeConfig(config);
    }

    private void replaceGlobalMcpServer(String configKey, JsonObject server) throws IOException {
        List<JsonObject> currentServers = "codexMcpServers".equals(configKey)
                ? getGlobalCodexMcpSource()
                : getGlobalClaudeMcpSource();

        java.util.List<JsonObject> updatedServers = new java.util.ArrayList<>();
        boolean replaced = false;
        String targetId = server.get("id").getAsString();
        for (JsonObject current : currentServers) {
            if (current.has("id") && targetId.equals(current.get("id").getAsString())) {
                updatedServers.add(server.deepCopy());
                replaced = true;
            } else {
                updatedServers.add(current.deepCopy());
            }
        }

        if (!replaced) {
            updatedServers.add(server.deepCopy());
        }

        writeGlobalMcpSource(configKey, updatedServers);
    }

    private boolean deleteGlobalMcpServer(String configKey, String serverId) throws IOException {
        List<JsonObject> currentServers = "codexMcpServers".equals(configKey)
                ? getGlobalCodexMcpSource()
                : getGlobalClaudeMcpSource();

        java.util.List<JsonObject> updatedServers = new java.util.ArrayList<>();
        boolean removed = false;
        for (JsonObject current : currentServers) {
            if (current.has("id") && serverId.equals(current.get("id").getAsString())) {
                removed = true;
            } else {
                updatedServers.add(current.deepCopy());
            }
        }

        if (removed) {
            writeGlobalMcpSource(configKey, updatedServers);
        }
        return removed;
    }

    private List<JsonObject> jsonArrayToObjectList(JsonArray array) {
        java.util.List<JsonObject> result = new java.util.ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                result.add(element.getAsJsonObject().deepCopy());
            }
        }
        return result;
    }

    @FunctionalInterface
    private interface IOListSupplier {
        List<JsonObject> get() throws IOException;
    }

    // ==================== Skills Management ====================

    public List<JsonObject> getSkills() throws IOException {
        return skillManager.getSkills();
    }

    public void upsertSkill(JsonObject skill) throws IOException {
        skillManager.upsertSkill(skill);
    }

    public boolean deleteSkill(String id) throws IOException {
        return skillManager.deleteSkill(id);
    }

    public Map<String, Object> validateSkill(JsonObject skill) {
        return skillManager.validateSkill(skill);
    }

    public void syncSkillsToClaudeSettings() throws IOException {
        skillManager.syncSkillsToClaudeSettings();
    }

    // ==================== Agents Management ====================

    public List<JsonObject> getAgents() throws IOException {
        return agentManager.getAgents();
    }

    public void addAgent(JsonObject agent) throws IOException {
        agentManager.addAgent(agent);
    }

    public void updateAgent(String id, JsonObject updates) throws IOException {
        agentManager.updateAgent(id, updates);
    }

    public boolean deleteAgent(String id) throws IOException {
        return agentManager.deleteAgent(id);
    }

    public JsonObject getAgent(String id) throws IOException {
        return agentManager.getAgent(id);
    }

    public String getSelectedAgentId() throws IOException {
        return agentManager.getSelectedAgentId();
    }

    public void setSelectedAgentId(String agentId) throws IOException {
        agentManager.setSelectedAgentId(agentId);
    }

    public AgentManager getAgentManager() {
        return agentManager;
    }

    // ==================== Prompts Management ====================

    /**
     * Get a PromptManager for the specified scope.
     * Creates managers on-demand using PromptManagerFactory.
     *
     * @param scope   The prompt scope (GLOBAL or PROJECT)
     * @param project The IntelliJ Project instance (required for PROJECT scope, can be null for GLOBAL scope)
     * @return An AbstractPromptManager instance for the specified scope
     */
    public AbstractPromptManager getPromptManager(PromptScope scope, Project project) {
        return PromptManagerFactory.create(scope, gson, pathManager, project);
    }

    /**
     * Get prompts from the specified scope.
     *
     * @param scope   The prompt scope (GLOBAL or PROJECT)
     * @param project The IntelliJ Project instance (required for PROJECT scope, can be null for GLOBAL scope)
     * @return List of prompts
     * @throws IOException if reading fails
     */
    public List<JsonObject> getPrompts(PromptScope scope, Project project) throws IOException {
        return getPromptManager(scope, project).getPrompts();
    }

    /**
     * Add a prompt to the specified scope.
     *
     * @param prompt  The prompt to add
     * @param scope   The prompt scope (GLOBAL or PROJECT)
     * @param project The IntelliJ Project instance (required for PROJECT scope, can be null for GLOBAL scope)
     * @throws IOException if writing fails
     */
    public void addPrompt(JsonObject prompt, PromptScope scope, Project project) throws IOException {
        getPromptManager(scope, project).addPrompt(prompt);
    }

    /**
     * Update a prompt in the specified scope.
     *
     * @param id      The prompt ID
     * @param updates The updates to apply
     * @param scope   The prompt scope (GLOBAL or PROJECT)
     * @param project The IntelliJ Project instance (required for PROJECT scope, can be null for GLOBAL scope)
     * @throws IOException if writing fails
     */
    public void updatePrompt(String id, JsonObject updates, PromptScope scope, Project project) throws IOException {
        getPromptManager(scope, project).updatePrompt(id, updates);
    }

    /**
     * Delete a prompt from the specified scope.
     *
     * @param id      The prompt ID
     * @param scope   The prompt scope (GLOBAL or PROJECT)
     * @param project The IntelliJ Project instance (required for PROJECT scope, can be null for GLOBAL scope)
     * @return true if deleted, false if not found
     * @throws IOException if writing fails
     */
    public boolean deletePrompt(String id, PromptScope scope, Project project) throws IOException {
        return getPromptManager(scope, project).deletePrompt(id);
    }

    /**
     * Get a prompt by ID from the specified scope.
     *
     * @param id      The prompt ID
     * @param scope   The prompt scope (GLOBAL or PROJECT)
     * @param project The IntelliJ Project instance (required for PROJECT scope, can be null for GLOBAL scope)
     * @return The prompt JsonObject, or null if not found
     * @throws IOException if reading fails
     */
    public JsonObject getPrompt(String id, PromptScope scope, Project project) throws IOException {
        return getPromptManager(scope, project).getPrompt(id);
    }

    /**
     * Batch import prompts to the specified scope.
     *
     * @param promptsToImport The prompts to import
     * @param strategy        The conflict resolution strategy
     * @param scope           The prompt scope (GLOBAL or PROJECT)
     * @param project         The IntelliJ Project instance (required for PROJECT scope, can be null for GLOBAL scope)
     * @return A map containing the results of the import operation
     * @throws IOException if writing fails
     */
    public Map<String, Object> batchImportPrompts(List<JsonObject> promptsToImport, ConflictStrategy strategy, PromptScope scope, Project project) throws IOException {
        return getPromptManager(scope, project).batchImportPrompts(promptsToImport, strategy);
    }

    /**
     * 用全局 Prompt 配置覆盖项目 Prompt 配置。
     *
     * @param project 当前项目
     * @throws IOException 读写失败
     */
    public void loadProjectPromptsFromGlobal(Project project) throws IOException {
        AbstractPromptManager globalManager = getPromptManager(PromptScope.GLOBAL, null);
        AbstractPromptManager projectManager = getPromptManager(PromptScope.PROJECT, project);
        projectManager.writePromptConfig(globalManager.readPromptConfig().deepCopy());
    }

    /**
     * 将项目 Prompt 配置写回全局 Prompt 配置。
     *
     * @param project 当前项目
     * @throws IOException 读写失败
     */
    public void saveProjectPromptsToGlobal(Project project) throws IOException {
        AbstractPromptManager globalManager = getPromptManager(PromptScope.GLOBAL, null);
        AbstractPromptManager projectManager = getPromptManager(PromptScope.PROJECT, project);
        globalManager.writePromptConfig(projectManager.readPromptConfig().deepCopy());
    }

    // ==================== Deprecated Backward-Compatible Methods ====================

    /**
     * Get a PromptManager (defaults to GLOBAL scope).
     *
     * @deprecated Use {@link #getPromptManager(PromptScope, Project)} instead
     */
    @Deprecated
    public AbstractPromptManager getPromptManager() {
        return getPromptManager(PromptScope.GLOBAL, null);
    }

    /**
     * Get prompts (defaults to GLOBAL scope).
     *
     * @deprecated Use {@link #getPrompts(PromptScope, Project)} instead
     */
    @Deprecated
    public List<JsonObject> getPrompts() throws IOException {
        return getPrompts(PromptScope.GLOBAL, null);
    }

    /**
     * Add a prompt (defaults to GLOBAL scope).
     *
     * @deprecated Use {@link #addPrompt(JsonObject, PromptScope, Project)} instead
     */
    @Deprecated
    public void addPrompt(JsonObject prompt) throws IOException {
        addPrompt(prompt, PromptScope.GLOBAL, null);
    }

    /**
     * Update a prompt (defaults to GLOBAL scope).
     *
     * @deprecated Use {@link #updatePrompt(String, JsonObject, PromptScope, Project)} instead
     */
    @Deprecated
    public void updatePrompt(String id, JsonObject updates) throws IOException {
        updatePrompt(id, updates, PromptScope.GLOBAL, null);
    }

    /**
     * Delete a prompt (defaults to GLOBAL scope).
     *
     * @deprecated Use {@link #deletePrompt(String, PromptScope, Project)} instead
     */
    @Deprecated
    public boolean deletePrompt(String id) throws IOException {
        return deletePrompt(id, PromptScope.GLOBAL, null);
    }

    /**
     * Get a prompt by ID (defaults to GLOBAL scope).
     *
     * @deprecated Use {@link #getPrompt(String, PromptScope, Project)} instead
     */
    @Deprecated
    public JsonObject getPrompt(String id) throws IOException {
        return getPrompt(id, PromptScope.GLOBAL, null);
    }

    // ==================== Sound Notification Management ====================

    /**
     * Get whether sound notification is enabled.
     *
     * @return whether sound notification is enabled, default is false
     */
    public boolean getSoundNotificationEnabled() throws IOException {
        JsonObject config = readConfig();

        if (!config.has("soundNotification")) {
            return false;
        }

        JsonObject soundConfig = config.getAsJsonObject("soundNotification");
        if (soundConfig.has("enabled")) {
            return soundConfig.get("enabled").getAsBoolean();
        }

        return false;
    }

    /**
     * Set whether sound notification is enabled.
     *
     * @param enabled whether to enable
     */
    public void setSoundNotificationEnabled(boolean enabled) throws IOException {
        JsonObject config = readConfig();

        JsonObject soundConfig;
        if (config.has("soundNotification")) {
            soundConfig = config.getAsJsonObject("soundNotification");
        } else {
            soundConfig = new JsonObject();
            config.add("soundNotification", soundConfig);
        }

        soundConfig.addProperty("enabled", enabled);
        writeConfig(config);
        LOG.info("[CodemossSettings] Set sound notification enabled: " + enabled);
    }

    /**
     * Get custom sound file path.
     *
     * @return custom sound path, null means use default sound
     */
    public String getCustomSoundPath() throws IOException {
        JsonObject config = readConfig();

        if (!config.has("soundNotification")) {
            return null;
        }

        JsonObject soundConfig = config.getAsJsonObject("soundNotification");
        if (soundConfig.has("customSoundPath") && !soundConfig.get("customSoundPath").isJsonNull()) {
            return soundConfig.get("customSoundPath").getAsString();
        }

        return null;
    }

    /**
     * Set custom sound file path.
     *
     * @param path file path, null means use default sound
     */
    public void setCustomSoundPath(String path) throws IOException {
        JsonObject config = readConfig();

        JsonObject soundConfig;
        if (config.has("soundNotification")) {
            soundConfig = config.getAsJsonObject("soundNotification");
        } else {
            soundConfig = new JsonObject();
            config.add("soundNotification", soundConfig);
        }

        if (path == null || path.isEmpty()) {
            soundConfig.remove("customSoundPath");
        } else {
            soundConfig.addProperty("customSoundPath", path);
        }

        writeConfig(config);
        LOG.info("[CodemossSettings] Set custom sound path: " + path);
    }

    /**
     * Get whether sound should only play when IDE window is not focused.
     *
     * @return whether only-when-unfocused is enabled, default is false
     */
    public boolean getSoundOnlyWhenUnfocused() throws IOException {
        JsonObject config = readConfig();

        if (!config.has("soundNotification")) {
            return false;
        }

        JsonObject soundConfig = config.getAsJsonObject("soundNotification");
        if (soundConfig.has("onlyWhenUnfocused")) {
            return soundConfig.get("onlyWhenUnfocused").getAsBoolean();
        }

        return false;
    }

    /**
     * Set whether sound should only play when IDE window is not focused.
     *
     * @param enabled whether to enable
     */
    public void setSoundOnlyWhenUnfocused(boolean enabled) throws IOException {
        JsonObject config = readConfig();

        JsonObject soundConfig;
        if (config.has("soundNotification")) {
            soundConfig = config.getAsJsonObject("soundNotification");
        } else {
            soundConfig = new JsonObject();
            config.add("soundNotification", soundConfig);
        }

        soundConfig.addProperty("onlyWhenUnfocused", enabled);
        writeConfig(config);
        LOG.info("[CodemossSettings] Set sound only when unfocused: " + enabled);
    }

    /**
     * Get selected sound ID.
     *
     * @return sound ID (e.g. "default", "chime", "bell", "ding", "success", "custom"), defaults to "default"
     */
    public String getSelectedSound() throws IOException {
        JsonObject config = readConfig();

        if (!config.has("soundNotification")) {
            return "default";
        }

        JsonObject soundConfig = config.getAsJsonObject("soundNotification");
        if (soundConfig.has("selectedSound") && !soundConfig.get("selectedSound").isJsonNull()) {
            return soundConfig.get("selectedSound").getAsString();
        }

        return "default";
    }

    /**
     * Set selected sound ID.
     *
     * @param soundId sound ID, null or empty means "default"
     */
    public void setSelectedSound(String soundId) throws IOException {
        JsonObject config = readConfig();

        JsonObject soundConfig;
        if (config.has("soundNotification")) {
            soundConfig = config.getAsJsonObject("soundNotification");
        } else {
            soundConfig = new JsonObject();
            config.add("soundNotification", soundConfig);
        }

        soundConfig.addProperty("selectedSound", (soundId == null || soundId.isEmpty()) ? "default" : soundId);
        writeConfig(config);
        LOG.info("[CodemossSettings] Set selected sound: " + soundId);
    }

    // ==================== Codex Provider Management ====================

    public List<JsonObject> getCodexProviders() throws IOException {
        return codexProviderManager.getCodexProviders();
    }

    public JsonObject getActiveCodexProvider() throws IOException {
        return codexProviderManager.getActiveCodexProvider();
    }

    public void addCodexProvider(JsonObject provider) throws IOException {
        codexProviderManager.addCodexProvider(provider);
    }

    public void saveCodexProvider(JsonObject provider) throws IOException {
        codexProviderManager.saveCodexProvider(provider);
    }

    public void updateCodexProvider(String id, JsonObject updates) throws IOException {
        codexProviderManager.updateCodexProvider(id, updates);
    }

    public DeleteResult deleteCodexProvider(String id) {
        return codexProviderManager.deleteCodexProvider(id);
    }

    public void switchCodexProvider(String id) throws IOException {
        codexProviderManager.switchCodexProvider(id);
    }

    public void applyActiveProviderToCodexSettings() throws IOException {
        codexProviderManager.applyActiveProviderToCodexSettings();
    }

    public JsonObject getCurrentCodexConfig() throws IOException {
        return codexProviderManager.getCurrentCodexConfig();
    }

    public int saveCodexProviders(List<JsonObject> providers) throws IOException {
        return codexProviderManager.saveProviders(providers);
    }

    public void saveCodexProviderOrder(List<String> orderedIds) throws IOException {
        codexProviderManager.saveProviderOrder(orderedIds);
    }
}
