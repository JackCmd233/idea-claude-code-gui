package com.github.claudecodegui.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Tab State Persistence Service.
 * Saves and restores custom tab names at the project level.
 * This is the legacy service; new code should prefer GlobalTabStateService.
 */
@State(
    name = "ClaudeCodeTabState",
    storages = @Storage("claudeCodeTabState.xml")
)
@Service(Service.Level.PROJECT)
public final class TabStateService implements PersistentStateComponent<TabStateService.State> {

    private static final Logger LOG = Logger.getInstance(TabStateService.class);

    private State myState = new State();

    public static TabStateService getInstance(@NotNull Project project) {
        return project.getService(TabStateService.class);
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
        LOG.info("[TabStateService] Loaded tab state with " + state.tabNames.size() + " entries");
    }

    /**
     * Save a tab name.
     * @param tabIndex the tab index
     * @param tabName the tab name
     */
    public void saveTabName(int tabIndex, String tabName) {
        if (tabName != null && !tabName.trim().isEmpty()) {
            myState.tabNames.put(tabIndex, tabName);
            LOG.info("[TabStateService] Saved tab name: index=" + tabIndex + ", name=" + tabName);
        }
    }

    /**
     * Get a tab name.
     * @param tabIndex the tab index
     * @return the tab name, or null if not set
     */
    @Nullable
    public String getTabName(int tabIndex) {
        return myState.tabNames.get(tabIndex);
    }

    /**
     * Remove a tab name.
     * @param tabIndex the tab index
     */
    public void removeTabName(int tabIndex) {
        myState.tabNames.remove(tabIndex);
        LOG.info("[TabStateService] Removed tab name for index: " + tabIndex);
    }

    /**
     * Get all tab names.
     * @return a map from tab index to tab name
     */
    public Map<Integer, String> getAllTabNames() {
        return new HashMap<>(myState.tabNames);
    }

    /**
     * Clear all tab names.
     */
    public void clearAllTabNames() {
        myState.tabNames.clear();
        LOG.info("[TabStateService] Cleared all tab names");
    }

    /**
     * Update tab indexes when a tab is removed (re-maps all indexes accordingly).
     *
     * @param removedIndex the index of the removed tab
     */
    public void onTabRemoved(int removedIndex) {
        // Remove the name of the deleted tab
        myState.tabNames.remove(removedIndex);

        // Decrement all indexes greater than removedIndex by 1
        Map<Integer, String> newMap = new HashMap<>();
        for (Map.Entry<Integer, String> entry : myState.tabNames.entrySet()) {
            int oldIndex = entry.getKey();
            int newIndex = oldIndex > removedIndex ? oldIndex - 1 : oldIndex;
            newMap.put(newIndex, entry.getValue());
        }
        myState.tabNames = newMap;

        // Update the tab count
        if (myState.tabCount > 0) {
            myState.tabCount--;
        }

        LOG.info("[TabStateService] Updated tab indexes after removal of index: "
                + removedIndex + ", new count: " + myState.tabCount);
    }

    /**
     * Save the tab count.
     * @param count the number of tabs
     */
    public void saveTabCount(int count) {
        myState.tabCount = count;
        LOG.info("[TabStateService] Saved tab count: " + count);
    }

    /**
     * Get the tab count.
     * @return the number of tabs, defaults to 1
     */
    public int getTabCount() {
        return Math.max(1, myState.tabCount);
    }

    /**
     * 判断当前项目是否存在可迁移的旧标签数据。
     *
     * @return true 表示存在旧标签状态
     */
    public boolean hasLegacyTabState() {
        return !myState.tabNames.isEmpty() || myState.tabCount > 1;
    }

    /**
     * 是否已完成全局标签迁移。
     *
     * @return true 表示已迁移
     */
    public boolean isMigratedToGlobal() {
        return myState.migratedToGlobal;
    }

    /**
     * 标记当前项目已完成全局标签迁移。
     */
    public void markMigratedToGlobal() {
        myState.migratedToGlobal = true;
        LOG.info("[TabStateService] Marked project tab state as migrated to global");
    }

    /**
     * Persistent state class.
     */
    public static class State {
        /**
         * Map from tab index to tab name.
         */
        public Map<Integer, String> tabNames = new HashMap<>();

        /**
         * Number of tabs.
         */
        public int tabCount = 1;

        /**
         * 是否已迁移到全局标签状态。
         */
        public boolean migratedToGlobal = false;
    }
}
