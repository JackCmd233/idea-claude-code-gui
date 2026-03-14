package com.github.claudecodegui.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 全局标签页状态协调工具。
 * 负责旧项目级标签数据迁移、去重和排序。
 */
public final class GlobalTabStateCoordinator {

    private static final String DEFAULT_TAB_PREFIX = "AI";

    private GlobalTabStateCoordinator() {
        // Utility class, prevent instantiation
    }

    /**
     * 合并全局标签和旧项目级标签。
     * 同名标签视为同一标签，按最近修改时间倒序排列。
     *
     * @param globalTabs 全局标签快照
     * @param legacyTabNames 项目级旧标签名映射
     * @param legacyTabCount 项目级旧标签数量
     * @param baseTimestamp 迁移基准时间
     * @return 合并后的标签列表
     */
    @NotNull
    public static List<GlobalTabStateService.GlobalTabRecord> mergeTabs(
            @NotNull List<GlobalTabStateService.GlobalTabRecord> globalTabs,
            @NotNull Map<Integer, String> legacyTabNames,
            int legacyTabCount,
            long baseTimestamp
    ) {
        Map<String, GlobalTabStateService.GlobalTabRecord> mergedByName = new LinkedHashMap<>();

        for (GlobalTabStateService.GlobalTabRecord globalTab : globalTabs) {
            if (!isValidTabName(globalTab.getName())) {
                continue;
            }
            mergedByName.put(globalTab.getName(), globalTab.copy());
        }

        List<GlobalTabStateService.GlobalTabRecord> legacyTabs = createLegacyTabRecords(
                legacyTabNames, legacyTabCount, baseTimestamp);
        for (GlobalTabStateService.GlobalTabRecord legacyTab : legacyTabs) {
            GlobalTabStateService.GlobalTabRecord existing = mergedByName.get(legacyTab.getName());
            if (existing == null) {
                mergedByName.put(legacyTab.getName(), legacyTab);
                continue;
            }

            // 中文注释：同名标签保留较新的修改时间，优先复用全局稳定 ID
            if (legacyTab.getLastModifiedAt() > existing.getLastModifiedAt()) {
                existing.setLastModifiedAt(legacyTab.getLastModifiedAt());
            }
        }

        List<GlobalTabStateService.GlobalTabRecord> merged = new ArrayList<>(mergedByName.values());
        merged.sort(
                Comparator.comparingLong(GlobalTabStateService.GlobalTabRecord::getLastModifiedAt)
                        .reversed()
                        .thenComparing(GlobalTabStateService.GlobalTabRecord::getName)
        );
        return ensureAtLeastOneTab(merged, baseTimestamp);
    }

    /**
     * 将旧项目级标签状态转换为带时间戳的全局标签记录。
     *
     * @param legacyTabNames 项目级旧标签名映射
     * @param legacyTabCount 项目级旧标签数量
     * @param baseTimestamp 迁移基准时间
     * @return 转换后的标签记录
     */
    @NotNull
    public static List<GlobalTabStateService.GlobalTabRecord> createLegacyTabRecords(
            @NotNull Map<Integer, String> legacyTabNames,
            int legacyTabCount,
            long baseTimestamp
    ) {
        List<GlobalTabStateService.GlobalTabRecord> legacyTabs = new ArrayList<>();

        int safeCount = Math.max(1, legacyTabCount);
        for (int index = 0; index < safeCount; index++) {
            String tabName = normalizeTabName(legacyTabNames.get(index));
            if (tabName == null) {
                tabName = DEFAULT_TAB_PREFIX + (index + 1);
            }

            // 中文注释：旧数据没有修改时间，这里按原索引顺序生成递减时间戳以保持稳定顺序。
            long lastModifiedAt = baseTimestamp - index;
            legacyTabs.add(new GlobalTabStateService.GlobalTabRecord(
                    UUID.randomUUID().toString(),
                    tabName,
                    lastModifiedAt
            ));
        }
        return legacyTabs;
    }

    /**
     * 确保标签列表至少包含一个默认标签。
     *
     * @param tabs 标签列表
     * @param baseTimestamp 基准时间
     * @return 至少包含一个标签的列表
     */
    @NotNull
    public static List<GlobalTabStateService.GlobalTabRecord> ensureAtLeastOneTab(
            @NotNull List<GlobalTabStateService.GlobalTabRecord> tabs,
            long baseTimestamp
    ) {
        if (!tabs.isEmpty()) {
            return new ArrayList<>(tabs);
        }

        List<GlobalTabStateService.GlobalTabRecord> defaults = new ArrayList<>();
        defaults.add(new GlobalTabStateService.GlobalTabRecord(
                UUID.randomUUID().toString(),
                DEFAULT_TAB_PREFIX + "1",
                baseTimestamp
        ));
        return defaults;
    }

    private static boolean isValidTabName(@Nullable String tabName) {
        return normalizeTabName(tabName) != null;
    }

    @Nullable
    private static String normalizeTabName(@Nullable String tabName) {
        if (tabName == null) {
            return null;
        }
        String trimmed = tabName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
