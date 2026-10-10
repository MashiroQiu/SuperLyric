package com.hchen.hooktool;

final class ProcessPackages {
    private ProcessPackages() {}
    static boolean belongsTo(String packageName, String processName) {
        return packageName != null && processName != null &&
            (processName.equals(packageName) || processName.startsWith(packageName + ":"));
    }
}
