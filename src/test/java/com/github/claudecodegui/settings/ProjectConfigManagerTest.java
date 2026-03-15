package com.github.claudecodegui.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ProjectConfigManager 回归测试。
 */
public class ProjectConfigManagerTest {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    /**
     * 首次初始化项目配置时，应复制全局默认值并迁移旧版按 projectPath 存储的项目级配置。
     */
    @Test
    public void shouldInitializeProjectConfigFromGlobalDefaultsAndLegacyOverrides() throws Exception {
        Path projectDir = Files.createTempDirectory("project-config-init");
        ProjectConfigManager manager = new ProjectConfigManager(gson, new ConfigPathManager());

        JsonObject globalConfig = new JsonObject();
        JsonObject streaming = new JsonObject();
        streaming.addProperty("default", false);
        streaming.addProperty(projectDir.toString(), true);
        globalConfig.add("streaming", streaming);

        JsonObject autoOpenFile = new JsonObject();
        autoOpenFile.addProperty("default", true);
        globalConfig.add("autoOpenFile", autoOpenFile);

        JsonObject sandboxMode = new JsonObject();
        sandboxMode.addProperty("default", "workspace-write");
        sandboxMode.addProperty(projectDir.toString(), "danger-full-access");
        globalConfig.add("codexSandboxMode", sandboxMode);

        JsonObject workingDirectories = new JsonObject();
        workingDirectories.addProperty(projectDir.toString(), ".agent");
        globalConfig.add("workingDirectories", workingDirectories);

        globalConfig.addProperty("commitPrompt", "global prompt");

        JsonObject projectConfig = manager.ensureProjectConfigInitialized(projectDir.toString(), globalConfig);

        assertTrue(manager.isProjectConfigInitialized(projectDir.toString()));
        assertEquals(true, projectConfig.get("streamingEnabled").getAsBoolean());
        assertEquals(true, projectConfig.get("autoOpenFileEnabled").getAsBoolean());
        assertEquals("danger-full-access", projectConfig.get("codexSandboxMode").getAsString());
        assertEquals(".agent", projectConfig.get("customWorkingDirectory").getAsString());
        assertEquals("global prompt", projectConfig.get("commitPrompt").getAsString());
    }

    /**
     * 已存在项目配置时，不应在初始化时被全局默认值覆盖。
     */
    @Test
    public void shouldKeepExistingProjectConfigWhenAlreadyInitialized() throws Exception {
        Path projectDir = Files.createTempDirectory("project-config-existing");
        ProjectConfigManager manager = new ProjectConfigManager(gson, new ConfigPathManager());

        JsonObject existingConfig = new JsonObject();
        existingConfig.addProperty("streamingEnabled", true);
        existingConfig.addProperty("commitPrompt", "project prompt");
        manager.writeProjectConfig(projectDir.toString(), existingConfig);

        JsonObject globalConfig = new JsonObject();
        JsonObject streaming = new JsonObject();
        streaming.addProperty("default", false);
        globalConfig.add("streaming", streaming);
        globalConfig.addProperty("commitPrompt", "global prompt");

        JsonObject projectConfig = manager.ensureProjectConfigInitialized(projectDir.toString(), globalConfig);

        assertEquals(true, projectConfig.get("streamingEnabled").getAsBoolean());
        assertEquals("project prompt", projectConfig.get("commitPrompt").getAsString());
    }

    /**
     * 项目配置文件不存在时，应返回未初始化状态。
     */
    @Test
    public void shouldReportProjectConfigNotInitializedWhenFileMissing() throws Exception {
        Path projectDir = Files.createTempDirectory("project-config-missing");
        ProjectConfigManager manager = new ProjectConfigManager(gson, new ConfigPathManager());

        assertFalse(manager.isProjectConfigInitialized(projectDir.toString()));
    }
}
