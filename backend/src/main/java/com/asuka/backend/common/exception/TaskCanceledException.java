package com.asuka.backend.common.exception;

/**
 * 任务在下一个阶段边界发现已被取消时抛出，用于停止 worker。
 */
public class TaskCanceledException extends RuntimeException {

    public TaskCanceledException() {
        super("任务已取消");
    }
}
