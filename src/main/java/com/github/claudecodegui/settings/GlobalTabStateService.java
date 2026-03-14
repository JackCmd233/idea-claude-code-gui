package com.github.claudecodegui.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 全局聊天标签页持久化服务。
 * 在应用级别保存标签页名称、稳定 ID 和最近修改时间。
 */
@State(
        name = "ClaudeCodeGlobalTabState",
        storages = @Storage("claudeCodeGlobalTabState.xml")
)
@Service(Service.Level.APP)
public final class GlobalTabStateService implements PersistentStateComponent<GlobalTabStateService.State> {

    private static final Logger LOG = Logger.getInstance(GlobalTabStateService.class);

    private State myState = new State();

    /**
     * 获取全局标签页状态服务实例。
     *
     * @return 全局标签页状态服务
     */
    public static GlobalTabStateService getInstance() {
        return ApplicationManager.getApplication().getService(GlobalTabStateService.class);
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
        if (myState.tabs == null) {
            myState.tabs = new ArrayList<>();
        }
        LOG.info("[GlobalTabStateService] Loaded global tab state with " + myState.tabs.size() + " entries");
    }

    /**
     * 返回当前全局标签页快照。
     *
     * @return 标签快照
     */
    public synchronized @NotNull List<GlobalTabRecord> getTabRecords() {
        List<GlobalTabRecord> copies = new ArrayList<>();
        for (GlobalTabRecord tab : myState.tabs) {
            copies.add(tab.copy());
        }
        return copies;
    }

    /**
     * 判断是否已有全局标签数据。
     *
     * @return true 表示存在全局标签
     */
    public synchronized boolean hasTabs() {
        return !myState.tabs.isEmpty();
    }

    /**
     * 使用新的标签快照替换当前全局状态。
     *
     * @param tabRecords 新标签列表
     */
    public synchronized void replaceTabs(@NotNull List<GlobalTabRecord> tabRecords) {
        myState.tabs = new ArrayList<>();
        for (GlobalTabRecord record : tabRecords) {
            myState.tabs.add(record.copy());
        }
        LOG.info("[GlobalTabStateService] Replaced global tab state, count=" + myState.tabs.size());
    }

    /**
     * 若当前为空则初始化默认标签。
     *
     * @param baseTimestamp 基准时间
     * @return 初始化后的标签列表
     */
    public synchronized @NotNull List<GlobalTabRecord> ensureDefaultTabs(long baseTimestamp) {
        if (myState.tabs.isEmpty()) {
            myState.tabs = GlobalTabStateCoordinator.ensureAtLeastOneTab(myState.tabs, baseTimestamp);
            LOG.info("[GlobalTabStateService] Initialized default global tab state");
        }
        return getTabRecords();
    }

    /**
     * 新增一个全局标签。
     *
     * @param tabName 标签名
     * @return 新增的标签记录
     */
    public synchronized @NotNull GlobalTabRecord createTab(@NotNull String tabName) {
        GlobalTabRecord record = new GlobalTabRecord(
                UUID.randomUUID().toString(),
                tabName,
                System.currentTimeMillis()
        );
        myState.tabs.add(record);
        LOG.info("[GlobalTabStateService] Created global tab: " + tabName);
        return record.copy();
    }

    /**
     * 根据稳定 ID 重命名标签。
     *
     * @param tabId 标签稳定 ID
     * @param newName 新名称
     */
    public synchronized void renameTab(@Nullable String tabId, @NotNull String newName) {
        GlobalTabRecord record = findById(tabId);
        if (record == null) {
            LOG.warn("[GlobalTabStateService] Cannot rename tab, record not found: " + tabId);
            return;
        }
        record.setName(newName);
        record.setLastModifiedAt(System.currentTimeMillis());
        LOG.info("[GlobalTabStateService] Renamed global tab: id=" + tabId + ", name=" + newName);
    }

    /**
     * 根据稳定 ID 删除标签。
     *
     * @param tabId 标签稳定 ID
     */
    public synchronized void removeTab(@Nullable String tabId) {
        if (tabId == null || tabId.trim().isEmpty()) {
            return;
        }
        int originalSize = myState.tabs.size();
        myState.tabs.removeIf(tab -> tabId.equals(tab.getId()));
        if (myState.tabs.isEmpty()) {
            myState.tabs = GlobalTabStateCoordinator.ensureAtLeastOneTab(myState.tabs, System.currentTimeMillis());
        }
        if (myState.tabs.size() != originalSize) {
            LOG.info("[GlobalTabStateService] Removed global tab: id=" + tabId);
        }
    }

    /**
     * 通过索引查询标签名称。
     *
     * @param index 标签索引
     * @return 标签名称，如果索引无效则返回 null
     */
    public synchronized @Nullable String getTabName(int index) {
        if (index < 0 || index >= myState.tabs.size()) {
            return null;
        }
        return myState.tabs.get(index).getName();
    }

    /**
     * 根据稳定 ID 查找标签记录。
     *
     * @param tabId 标签稳定 ID
     * @return 标签记录，如果未找到则返回 null
     */
    private GlobalTabRecord findById(@Nullable String tabId) {
        if (tabId == null || tabId.trim().isEmpty()) {
            return null;
        }
        for (GlobalTabRecord tab : myState.tabs) {
            if (tabId.equals(tab.getId())) {
                return tab;
            }
        }
        return null;
    }

    /**
     * 全局标签记录。
     */
    public static class GlobalTabRecord {
        public String id;
        public String name;
        public long lastModifiedAt;

        public GlobalTabRecord() {
            // Default constructor for serialization
        }

        public GlobalTabRecord(String id, String name, long lastModifiedAt) {
            this.id = id;
            this.name = name;
            this.lastModifiedAt = lastModifiedAt;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getLastModifiedAt() {
            return lastModifiedAt;
        }

        public void setLastModifiedAt(long lastModifiedAt) {
            this.lastModifiedAt = lastModifiedAt;
        }

        /**
         * Creates a deep copy of this record.
         */
        public GlobalTabRecord copy() {
            return new GlobalTabRecord(id, name, lastModifiedAt);
        }
    }

    /**
     * 持久化状态结构。
     */
    public static class State {
        public List<GlobalTabRecord> tabs = new ArrayList<>();
    }
}
