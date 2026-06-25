package com.datalake.platform.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatchScheduler.class);

    private final TaskService taskService;

    public TaskDispatchScheduler(TaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedDelayString = "${app.task.poll-delay-ms:15000}")
    public void dispatchDueTasks() {
        try {
            taskService.executeDueTasks();
        } catch (Exception exception) {
            log.error("dispatch due tasks failed", exception);
        }
    }
}
