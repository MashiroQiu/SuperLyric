package com.hchen.hooktool;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProcessPackagesTest {
    @Test public void mainAndPlaybackProcessBelongToFirstPackage() {
        assertTrue(ProcessPackages.belongsTo("com.tencent.qqmusic", "com.tencent.qqmusic"));
        assertTrue(ProcessPackages.belongsTo("com.tencent.qqmusic", "com.tencent.qqmusic:QQPlayerService"));
    }
    @Test public void otherPackagesAndPrefixesDoNotBelong() {
        assertFalse(ProcessPackages.belongsTo("com.tencent.qqmusic", "com.tencent.qqmusic.other"));
        assertFalse(ProcessPackages.belongsTo("com.tencent.qqmusic", "com.netease.cloudmusic"));
        assertFalse(ProcessPackages.belongsTo(null, "com.tencent.qqmusic"));
    }
}
