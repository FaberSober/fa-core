package com.faber.core.service;

import com.faber.core.vo.socket.SocketTaskVo;

/**
 * WebSocket任务进度推送服务。
 */
public interface SocketTaskProgressService {

    void sendTaskProgress(SocketTaskVo socketTaskVo);

}
