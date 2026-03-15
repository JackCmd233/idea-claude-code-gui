package com.github.claudecodegui.settings;

import com.github.claudecodegui.ClaudeCodeGuiBundle;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 项目配置管理器。
 * 负责项目级 config.json 的初始化、读取、写入，以及旧版全局项目配置迁移。
 */
public class ProjectConfigManager {
    private static final Logger LOG = Logger.getInstance(ProjectConfigManager.class);

    private static final String STREAMING_ENABLED_KEY = "streamingEnabled";
    private static final String AUTO_OPEN_FILE_ENABLED_KEY = "autoOpenFileEnabled";
    private static final String CUSTOM_WORKING_DIRECTORY_KEY = "customWorkingDirectory";
    private static final String CODEX_SANDBOX_MODE_KEY = "codexSandboxMode";
    private static final String COMMIT_PROMPT_KEY = "commitPrompt";
    private static final String MCP_SERVERS_KEY = "mcpServers";
    private static final String CODEX_MCP_SERVERS_KEY = "codexMcpServers";

    private final Gson gson;
    private final ConfigPathManager pathManager;

    /**
     * 创建项目配置管理器。
     *
     * @param gson Gson 实例
     * @param pathManager 路径管理器
     */
    public ProjectConfigManager(Gson gson, ConfigPathManager pathManager) {
        this.gson = gson;
        this.pathManager = pathManager;
    }

    /**
     * 判断项目配置是否已经初始化。
     *
     * @param projectPath 项目根目录
     * @return 是否已初始化
     */
    public boolean isProjectConfigInitialized(String projectPath) {
        return Files.exists(pathManager.getProjectConfigFilePath(projectPath));
    }

    /**
     * 读取项目配置。
     *
     * @param projectPath 项目根目录
     * @return 项目配置 JSON，不存在时返回空对象
     * @throws IOException 文件读取失败
     */
    public JsonObject readProjectConfig(String projectPath) throws IOException {
        Path configPath = pathManager.getProjectConfigFilePath(projectPath);
        if (!Files.exists(configPath)) {
            return new JsonObject();
        }

        try (FileReader reader = new FileReader(configPath.toFile(), StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
        } catch (Exception e) {
            LOG.warn("[ProjectConfigManager] Failed to read project config, returning empty object: " + e.getMessage());
        }
        return new JsonObject();
    }

    /**
     * 写入项目配置。
     *
     * @param projectPath 项目根目录
     * @param projectConfig 目标配置
     * @throws IOException 文件写入失败
     */
    public void writeProjectConfig(String projectPath, JsonObject projectConfig) throws IOException {
        pathManager.ensureProjectConfigDirectory(projectPath);
        Path configPath = pathManager.getProjectConfigFilePath(projectPath);
        try (FileWriter writer = new FileWriter(configPath.toFile(), StandardCharsets.UTF_8)) {
            gson.toJson(projectConfig, writer);
        }
    }

    /**
     * 确保项目配置已初始化。
     * 如果项目文件不存在，则从全局配置生成一份项目快照，并迁移旧版按 projectPath 存储的配置。
     *
     * @param projectPath 项目根目录
     * @param globalConfig 当前全局配置
     * @return 项目配置内容
     * @throws IOException 文件读写失败
     */
    public JsonObject ensureProjectConfigInitialized(String projectPath, JsonObject globalConfig) throws IOException {
        if (isProjectConfigInitialized(projectPath)) {
            return readProjectConfig(projectPath);
        }

        JsonObject initialProjectConfig = buildInitialProjectConfig(projectPath, globalConfig);
        writeProjectConfig(projectPath, initialProjectConfig);
        LOG.info("[ProjectConfigManager] Initialized project config: " + pathManager.getProjectConfigFilePath(projectPath));
        return initialProjectConfig;
    }

    /**
     * 用全局配置覆盖项目配置中的基础设置。
     *
     * @param projectPath 项目根目录
     * @param globalConfig 全局配置
     * @return 覆盖后的项目配置
     * @throws IOException 文件读写失败
     */
    public JsonObject overwriteProjectBasicsFromGlobal(String projectPath, JsonObject globalConfig) throws IOException {
        JsonObject projectConfig = isProjectConfigInitialized(projectPath)
                ? readProjectConfig(projectPath)
                : new JsonObject();

        JsonObject imported = buildInitialProjectConfig(projectPath, globalConfig);
        copyBasicSetting(imported, projectConfig, STREAMING_ENABLED_KEY);
        copyBasicSetting(imported, projectConfig, AUTO_OPEN_FILE_ENABLED_KEY);
        copyBasicSetting(imported, projectConfig, CUSTOM_WORKING_DIRECTORY_KEY);
        copyBasicSetting(imported, projectConfig, CODEX_SANDBOX_MODE_KEY);
        copyBasicSetting(imported, projectConfig, COMMIT_PROMPT_KEY);

        writeProjectConfig(projectPath, projectConfig);
        return projectConfig;
    }

    /**
     * 将项目级 MCP 配置整体替换。
     *
     * @param projectPath 项目根目录
     * @param mcpServers Claude MCP 列表
     * @throws IOException 文件写入失败
     */
    public void setProjectMcpServers(String projectPath, JsonElement mcpServers) throws IOException {
        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath, new JsonObject());
        if (mcpServers == null) {
            projectConfig.remove(MCP_SERVERS_KEY);
        } else {
            projectConfig.add(MCP_SERVERS_KEY, mcpServers.deepCopy());
        }
        writeProjectConfig(projectPath, projectConfig);
    }

    /**
     * 将项目级 Codex MCP 配置整体替换。
     *
     * @param projectPath 项目根目录
     * @param mcpServers Codex MCP 列表
     * @throws IOException 文件写入失败
     */
    public void setProjectCodexMcpServers(String projectPath, JsonElement mcpServers) throws IOException {
        JsonObject projectConfig = ensureProjectConfigInitialized(projectPath, new JsonObject());
        if (mcpServers == null) {
            projectConfig.remove(CODEX_MCP_SERVERS_KEY);
        } else {
            projectConfig.add(CODEX_MCP_SERVERS_KEY, mcpServers.deepCopy());
        }
        writeProjectConfig(projectPath, projectConfig);
    }

    /**
     * 读取项目级 Claude MCP 配置。
     *
     * @param projectPath 项目根目录
     * @return 项目级 MCP 配置，不存在时返回 null
     * @throws IOException 文件读取失败
     */
    public JsonElement getProjectMcpServers(String projectPath) throws IOException {
        JsonObject projectConfig = readProjectConfig(projectPath);
        return projectConfig.has(MCP_SERVERS_KEY) ? projectConfig.get(MCP_SERVERS_KEY).deepCopy() : null;
    }

    /**
     * 读取项目级 Codex MCP 配置。
     *
     * @param projectPath 项目根目录
     * @return 项目级 Codex MCP 配置，不存在时返回 null
     * @throws IOException 文件读取失败
     */
    public JsonElement getProjectCodexMcpServers(String projectPath) throws IOException {
        JsonObject projectConfig = readProjectConfig(projectPath);
        return projectConfig.has(CODEX_MCP_SERVERS_KEY) ? projectConfig.get(CODEX_MCP_SERVERS_KEY).deepCopy() : null;
    }

    /**
     * 根据全局配置生成项目级初始化快照。
     *
     * @param projectPath 项目根目录
     * @param globalConfig 全局配置
     * @return 初始化后的项目配置
     */
    private JsonObject buildInitialProjectConfig(String projectPath, JsonObject globalConfig) {
        JsonObject projectConfig = new JsonObject();

        projectConfig.addProperty(STREAMING_ENABLED_KEY, extractLegacyBooleanSetting(globalConfig, "streaming", projectPath, true));
        projectConfig.addProperty(AUTO_OPEN_FILE_ENABLED_KEY, extractLegacyBooleanSetting(globalConfig, "autoOpenFile", projectPath, true));
        projectConfig.addProperty(
                CODEX_SANDBOX_MODE_KEY,
                extractLegacyStringSetting(globalConfig, "codexSandboxMode", projectPath, "workspace-write")
        );

        String workingDirectory = extractLegacyWorkingDirectory(globalConfig, projectPath);
        if (workingDirectory != null) {
            projectConfig.addProperty(CUSTOM_WORKING_DIRECTORY_KEY, workingDirectory);
        }

        String commitPrompt = extractCommitPrompt(globalConfig);
        if (commitPrompt != null) {
            projectConfig.addProperty(COMMIT_PROMPT_KEY, commitPrompt);
        }

        return projectConfig;
    }

    /**
     * 迁移旧版布尔型项目配置。
     */
    private boolean extractLegacyBooleanSetting(
            JsonObject globalConfig,
            String legacyKey,
            String projectPath,
            boolean defaultValue
    ) {
        if (!globalConfig.has(legacyKey) || !globalConfig.get(legacyKey).isJsonObject()) {
            return defaultValue;
        }

        JsonObject legacyObject = globalConfig.getAsJsonObject(legacyKey);
        if (legacyObject.has(projectPath) && !legacyObject.get(projectPath).isJsonNull()) {
            return legacyObject.get(projectPath).getAsBoolean();
        }
        if (legacyObject.has("default") && !legacyObject.get("default").isJsonNull()) {
            return legacyObject.get("default").getAsBoolean();
        }
        return defaultValue;
    }

    /**
     * 迁移旧版字符串型项目配置。
     */
    private String extractLegacyStringSetting(
            JsonObject globalConfig,
            String legacyKey,
            String projectPath,
            String defaultValue
    ) {
        if (!globalConfig.has(legacyKey) || !globalConfig.get(legacyKey).isJsonObject()) {
            return defaultValue;
        }

        JsonObject legacyObject = globalConfig.getAsJsonObject(legacyKey);
        if (legacyObject.has(projectPath) && !legacyObject.get(projectPath).isJsonNull()) {
            return legacyObject.get(projectPath).getAsString();
        }
        if (legacyObject.has("default") && !legacyObject.get("default").isJsonNull()) {
            return legacyObject.get("default").getAsString();
        }
        return defaultValue;
    }

    /**
     * 迁移旧版工作目录配置。
     */
    private String extractLegacyWorkingDirectory(JsonObject globalConfig, String projectPath) {
        if (!globalConfig.has("workingDirectories") || !globalConfig.get("workingDirectories").isJsonObject()) {
            return null;
        }

        JsonObject workingDirectories = globalConfig.getAsJsonObject("workingDirectories");
        if (workingDirectories.has(projectPath) && !workingDirectories.get(projectPath).isJsonNull()) {
            return workingDirectories.get(projectPath).getAsString();
        }
        if (workingDirectories.has("default") && !workingDirectories.get("default").isJsonNull()) {
            return workingDirectories.get("default").getAsString();
        }
        return null;
    }

    /**
     * 读取初始化时应写入项目配置的 Commit Prompt。
     */
    private String extractCommitPrompt(JsonObject globalConfig) {
        if (globalConfig.has(COMMIT_PROMPT_KEY) && !globalConfig.get(COMMIT_PROMPT_KEY).isJsonNull()) {
            return globalConfig.get(COMMIT_PROMPT_KEY).getAsString();
        }
        return ClaudeCodeGuiBundle.message("commit.defaultPrompt");
    }

    /**
     * 用导入值覆盖目标项目配置中的单个基础项。
     */
    private void copyBasicSetting(JsonObject imported, JsonObject target, String key) {
        if (imported.has(key)) {
            target.add(key, imported.get(key).deepCopy());
        } else {
            target.remove(key);
        }
    }
}
