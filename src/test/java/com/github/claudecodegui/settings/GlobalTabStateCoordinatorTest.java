package com.github.claudecodegui.settings;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * 全局标签页状态协调与服务测试。
 */
public class GlobalTabStateCoordinatorTest {

    /**
     * 旧项目级标签迁移时，应按原索引顺序生成递减时间戳。
     */
    @Test
    public void shouldCreateLegacyRecordsWithStableOrder() {
        Map<Integer, String> legacyTabNames = new HashMap<>();
        legacyTabNames.put(0, "后端联调");
        legacyTabNames.put(1, "接口排查");

        List<GlobalTabStateService.GlobalTabRecord> records =
                GlobalTabStateCoordinator.createLegacyTabRecords(legacyTabNames, 3, 1_000L);

        assertEquals(3, records.size());
        assertEquals("后端联调", records.get(0).getName());
        assertEquals("接口排查", records.get(1).getName());
        assertEquals("AI3", records.get(2).getName());
        assertTrue(records.get(0).getLastModifiedAt() > records.get(1).getLastModifiedAt());
        assertTrue(records.get(1).getLastModifiedAt() > records.get(2).getLastModifiedAt());
    }

    /**
     * 合并全局和项目级标签时，应同名去重并按最近修改时间倒序排列。
     */
    @Test
    public void shouldMergeTabsByNameAndSortByLastModifiedDesc() {
        List<GlobalTabStateService.GlobalTabRecord> globalTabs = List.of(
                new GlobalTabStateService.GlobalTabRecord("g1", "AI1", 10L),
                new GlobalTabStateService.GlobalTabRecord("g2", "联调窗口", 30L)
        );

        Map<Integer, String> legacyTabNames = new HashMap<>();
        legacyTabNames.put(0, "历史会话");
        legacyTabNames.put(1, "AI1");

        List<GlobalTabStateService.GlobalTabRecord> merged =
                GlobalTabStateCoordinator.mergeTabs(globalTabs, legacyTabNames, 2, 100L);

        assertEquals(3, merged.size());
        assertEquals("历史会话", merged.get(0).getName());
        assertEquals("AI1", merged.get(1).getName());
        assertEquals("联调窗口", merged.get(2).getName());
        assertEquals("g1", merged.get(1).getId());
        assertEquals(99L, merged.get(1).getLastModifiedAt());
    }

    /**
     * 全局服务应支持新增、重命名和删除标签，并在删除最后一个标签时补回默认标签。
     */
    @Test
    public void shouldCreateRenameAndRemoveGlobalTabs() {
        GlobalTabStateService service = new GlobalTabStateService();

        List<GlobalTabStateService.GlobalTabRecord> defaults = service.ensureDefaultTabs(500L);
        assertEquals(1, defaults.size());
        assertEquals("AI1", defaults.get(0).getName());

        GlobalTabStateService.GlobalTabRecord created = service.createTab("项目 B");
        List<GlobalTabStateService.GlobalTabRecord> afterCreate = service.getTabRecords();
        assertEquals(2, afterCreate.size());
        assertEquals("项目 B", afterCreate.get(1).getName());

        service.renameTab(created.getId(), "项目 B 重命名");
        List<GlobalTabStateService.GlobalTabRecord> afterRename = service.getTabRecords();
        assertEquals("项目 B 重命名", afterRename.get(1).getName());
        assertTrue(afterRename.get(1).getLastModifiedAt() >= created.getLastModifiedAt());

        service.removeTab(created.getId());
        List<GlobalTabStateService.GlobalTabRecord> afterRemove = service.getTabRecords();
        assertEquals(1, afterRemove.size());
        assertEquals("AI1", afterRemove.get(0).getName());

        String defaultId = afterRemove.get(0).getId();
        service.removeTab(defaultId);
        List<GlobalTabStateService.GlobalTabRecord> afterRemovingLast = service.getTabRecords();
        assertEquals(1, afterRemovingLast.size());
        assertEquals("AI1", afterRemovingLast.get(0).getName());
        assertNotEquals(defaultId, afterRemovingLast.get(0).getId());
    }
}
