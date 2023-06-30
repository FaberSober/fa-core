package com.faber.core.vo.socket;

import java.util.HashSet;
import java.util.Set;

public class SocketTaskHolder {

    public static final Set<String> STOP_TASK_ID = new HashSet<>();

    public static boolean isTaskStop(String taskId) {
        return STOP_TASK_ID.contains(taskId);
    }

    public static void stopTask(String taskId) {
        STOP_TASK_ID.add(taskId);
    }

    public static void removeTask(String taskId) {
        STOP_TASK_ID.remove(taskId);
    }

}
