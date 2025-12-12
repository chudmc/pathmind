package com.pathmind.execution;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IGetToBlockProcess;
import baritone.api.process.IMineProcess;
import baritone.api.process.IExploreProcess;
import baritone.api.process.IFarmProcess;
import baritone.api.process.IBaritoneProcess;
import baritone.api.pathing.goals.Goal;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Tracks Baritone processes precisely by monitoring their actual state changes.
 * This provides exact completion detection instead of timeouts or approximations.
 */
public class PreciseCompletionTracker {
    
    private static PreciseCompletionTracker instance;
    private final Map<String, CompletableFuture<Void>> pendingTasks = new ConcurrentHashMap<>();
    private final Map<String, ProcessState> processStates = new ConcurrentHashMap<>();
    private final Map<String, Long> taskStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> taskCompletionGraceStarts = new ConcurrentHashMap<>();
    private final Map<String, String> taskTypes = new ConcurrentHashMap<>();
    private Timer monitoringTimer;
    
    // Task types
    public static final String TASK_GOTO = "goto";
    public static final String TASK_PATH = "path";
    public static final String TASK_GOAL = "goal";
    public static final String TASK_COLLECT = "collect";
    public static final String TASK_EXPLORE = "explore";
    public static final String TASK_FARM = "farm";
    
    // Maximum monitoring duration (in milliseconds) - safety fallback
    // Long-running nodes like Mine can legitimately take several minutes, so we allow a 60-minute window.
    private static final long MAX_MONITORING_DURATION = 3_600_000; // 60 minutes
    private static final long WARNING_THRESHOLD_MS = 300_000; // 5 minutes reminder window
    private static final long TASK_START_TIMEOUT_MS = 8000;
    private static final long COLLECT_COMPLETION_GRACE_MS = 750;
    
    private enum ProcessState {
        STARTING,
        ACTIVE,
        COMPLETING,
        COMPLETED,
        FAILED
    }

    private PreciseCompletionTracker() {
        this.monitoringTimer = new Timer("PreciseCompletionTimer", true);
    }
    
    public static PreciseCompletionTracker getInstance() {
        if (instance == null) {
            instance = new PreciseCompletionTracker();
        }
        return instance;
    }
    
    /**
     * Start tracking a task with precise completion detection
     */
    public void startTrackingTask(String taskType, CompletableFuture<Void> future) {
        String taskId = createTaskId(taskType);
        pendingTasks.put(taskId, future);
        processStates.put(taskId, ProcessState.STARTING);
        taskStartTimes.put(taskId, System.currentTimeMillis());
        taskTypes.put(taskId, taskType);

        System.out.println("PreciseCompletionTracker: Started tracking task: " + taskType + " (" + taskId + ")");

        // Start monitoring/tracking this specific task
        startMonitoringTask(taskId);
        startWarningTimer(taskId);
    }

    private String createTaskId(String taskType) {
        return taskType + ":" + java.util.UUID.randomUUID();
    }

    private String getTaskType(String taskId) {
        String storedType = taskTypes.get(taskId);
        if (storedType != null) {
            return storedType;
        }
        int separator = taskId.indexOf(':');
        if (separator > 0) {
            return taskId.substring(0, separator);
        }
        return taskId;
    }

    /**
     * Start monitoring a specific task
     */
    private void startMonitoringTask(String taskId) {
        // Schedule monitoring every 100ms for precise detection
        TimerTask monitoringTask = new TimerTask() {
            @Override
            public void run() {
                if (!pendingTasks.containsKey(taskId)) {
                    this.cancel();
                    return;
                }
                
                try {
                    if (checkTaskCompletion(taskId)) {
                        this.cancel();
                    }
                } catch (Exception e) {
                    System.err.println("Error monitoring task " + taskId + ": " + e.getMessage());
                    completeTaskWithError(taskId, "Monitoring error: " + e.getMessage());
                    this.cancel();
                }
            }
        };
        
        monitoringTimer.schedule(monitoringTask, 100, 100); // Start in 100ms, repeat every 100ms
    }

    private void startWarningTimer(String taskId) {
        if (WARNING_THRESHOLD_MS <= 0L) {
            return;
        }
        TimerTask warningTask = new TimerTask() {
            @Override
            public void run() {
                if (!pendingTasks.containsKey(taskId)) {
                    this.cancel();
                    return;
                }
                String taskType = getTaskType(taskId);
                String warning = "Long-running Pathmind task '" + taskType + "' has been running for over 5 minutes. Hold tight until it finishes.";
                System.out.println("PreciseCompletionTracker: " + warning);
                notifyPlayer(warning);
                this.cancel();
            }
        };
        monitoringTimer.schedule(warningTask, WARNING_THRESHOLD_MS);
    }

    /**
     * Check if a specific task has completed
     */
    private boolean checkTaskCompletion(String taskId) {
        IBaritone baritone = getBaritone();
        if (baritone == null) {
            completeTaskWithError(taskId, "Baritone not available");
            return true;
        }
        String taskType = getTaskType(taskId);
        
        // Check for timeout
        Long startTime = taskStartTimes.get(taskId);
        if (startTime != null && System.currentTimeMillis() - startTime > MAX_MONITORING_DURATION) {
            completeTaskWithError(taskId, "Task timed out after " + (MAX_MONITORING_DURATION / 1000) + " seconds");
            return true;
        }
        
        ProcessState currentState = processStates.get(taskId);
        if (currentState == ProcessState.COMPLETED || currentState == ProcessState.FAILED) {
            return true; // Already handled
        }
        
        boolean completed = false;
        ProcessState newState = currentState;
        
        switch (taskType) {
            case TASK_GOTO:
            case TASK_PATH:
                completed = checkPathingCompletion(baritone, taskId);
                break;
                
            case TASK_GOAL:
                completed = checkGoalCompletion(baritone, taskId);
                break;
                
            case TASK_COLLECT:
                completed = checkCollectCompletion(baritone, taskId);
                break;
                
            case TASK_EXPLORE:
                completed = checkExplorationCompletion(baritone, taskId);
                break;
                
            case TASK_FARM:
                completed = checkFarmingCompletion(baritone, taskId);
                break;
                
            default:
                System.err.println("Unknown task type: " + taskType + " (" + taskId + ")");
                completed = true;
                break;
        }
        
        if (completed) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if pathing tasks (goto/path) have completed
     */
    private boolean checkPathingCompletion(IBaritone baritone, String taskId) {
        IPathingBehavior pathingBehavior = baritone.getPathingBehavior();
        ICustomGoalProcess customGoalProcess = baritone.getCustomGoalProcess();
        IGetToBlockProcess getToBlockProcess = baritone.getGetToBlockProcess();

        // Check if pathing has stopped and no goal is active
        boolean hasPath = pathingBehavior.hasPath();
        boolean isPathing = pathingBehavior.isPathing();
        boolean isActive = customGoalProcess.isActive();
        boolean getToBlockActive = getToBlockProcess != null && getToBlockProcess.isActive();
        
        // Get current state
        ProcessState currentState = processStates.get(taskId);
        
        if (currentState == ProcessState.STARTING && (isActive || getToBlockActive)) {
            // Task has started
            processStates.put(taskId, ProcessState.ACTIVE);
            System.out.println("PreciseCompletionTracker: " + taskId + " is now active");
        } else if (currentState == ProcessState.ACTIVE && !isActive && !getToBlockActive && !hasPath && !isPathing) {
            // Task has completed - no longer active and no pathing happening
            System.out.println("PreciseCompletionTracker: " + taskId + " completed - no longer active");
            completeTask(taskId);
            return true;
        } else if (currentState == ProcessState.ACTIVE && !isActive && !getToBlockActive && hasPath) {
            // Task is finishing - no longer active but still has a path (might be reaching goal)
            processStates.put(taskId, ProcessState.COMPLETING);
            System.out.println("PreciseCompletionTracker: " + taskId + " is completing");
        } else if (currentState == ProcessState.COMPLETING && !hasPath && !isPathing && !getToBlockActive) {
            // Path finished - task completed
            System.out.println("PreciseCompletionTracker: " + taskId + " completed - path finished");
            completeTask(taskId);
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if goal setting has completed
     */
    private boolean checkGoalCompletion(IBaritone baritone, String taskId) {
        // Goal setting is immediate, so complete right away
        completeTask(taskId);
        return true;
    }
    
    /**
     * Check if mining has completed
     */
    private boolean checkCollectCompletion(IBaritone baritone, String taskId) {
        IMineProcess mineProcess = baritone.getMineProcess();
        if (mineProcess == null) {
            completeTaskWithError(taskId, "Collect process unavailable");
            return true;
        }

        ProcessState currentState = processStates.get(taskId);
        IPathingBehavior pathingBehavior = baritone.getPathingBehavior();
        ICustomGoalProcess customGoalProcess = baritone.getCustomGoalProcess();
        IGetToBlockProcess getToBlockProcess = baritone.getGetToBlockProcess();

        boolean miningActive = mineProcess.isActive();
        boolean pathingActive =
                (pathingBehavior != null && (pathingBehavior.isPathing() || pathingBehavior.hasPath()))
                || (customGoalProcess != null && customGoalProcess.isActive())
                || (getToBlockProcess != null && getToBlockProcess.isActive());
        boolean anyActive = miningActive || pathingActive;

        if (currentState == ProcessState.STARTING && anyActive) {
            processStates.put(taskId, ProcessState.ACTIVE);
            System.out.println("PreciseCompletionTracker: " + taskId + " is now active");
        } else if (currentState == ProcessState.STARTING && hasTaskExceededStartTimeout(taskId)) {
            failTaskGracefully(taskId, "Mine task never became active", "Mine task could not start. Make sure the target block exists nearby and Baritone isn't busy.");
            return true;
        } else if (currentState == ProcessState.ACTIVE) {
            if (!anyActive) {
                processStates.put(taskId, ProcessState.COMPLETING);
                taskCompletionGraceStarts.put(taskId, System.currentTimeMillis());
                System.out.println("PreciseCompletionTracker: " + taskId + " entering completion grace");
            }
        } else if (currentState == ProcessState.COMPLETING) {
            if (anyActive) {
                processStates.put(taskId, ProcessState.ACTIVE);
                taskCompletionGraceStarts.remove(taskId);
                System.out.println("PreciseCompletionTracker: " + taskId + " resumed mining during grace period");
            } else {
                Long graceStart = taskCompletionGraceStarts.get(taskId);
                if (graceStart == null) {
                    graceStart = System.currentTimeMillis();
                    taskCompletionGraceStarts.put(taskId, graceStart);
                }
                if (System.currentTimeMillis() - graceStart >= COLLECT_COMPLETION_GRACE_MS) {
                    System.out.println("PreciseCompletionTracker: " + taskId + " completed after grace period");
                    completeTask(taskId);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasTaskExceededStartTimeout(String taskId) {
        Long startTime = taskStartTimes.get(taskId);
        if (startTime == null) {
            return false;
        }
        return System.currentTimeMillis() - startTime > TASK_START_TIMEOUT_MS;
    }

    /**
     * Check if exploration has completed
     */
    private boolean checkExplorationCompletion(IBaritone baritone, String taskId) {
        IExploreProcess exploreProcess = baritone.getExploreProcess();
        
        ProcessState currentState = processStates.get(taskId);
        
        if (currentState == ProcessState.STARTING && exploreProcess.isActive()) {
            // Exploration has started
            processStates.put(taskId, ProcessState.ACTIVE);
            System.out.println("PreciseCompletionTracker: " + taskId + " is now active");
        } else if (currentState == ProcessState.ACTIVE && !exploreProcess.isActive()) {
            // Exploration has completed
            System.out.println("PreciseCompletionTracker: " + taskId + " completed - no longer active");
            completeTask(taskId);
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if farming has completed
     */
    private boolean checkFarmingCompletion(IBaritone baritone, String taskId) {
        IFarmProcess farmProcess = baritone.getFarmProcess();
        
        ProcessState currentState = processStates.get(taskId);
        
        if (currentState == ProcessState.STARTING && farmProcess.isActive()) {
            // Farming has started
            processStates.put(taskId, ProcessState.ACTIVE);
            System.out.println("PreciseCompletionTracker: " + taskId + " is now active");
        } else if (currentState == ProcessState.ACTIVE && !farmProcess.isActive()) {
            // Farming has completed
            System.out.println("PreciseCompletionTracker: " + taskId + " completed - no longer active");
            completeTask(taskId);
            return true;
        }
        
        return false;
    }
    
    /**
     * Complete a task successfully
     */
    private void completeTask(String taskId) {
        CompletableFuture<Void> future = pendingTasks.remove(taskId);
        processStates.remove(taskId);
        Long startTime = taskStartTimes.remove(taskId);
        taskCompletionGraceStarts.remove(taskId);
        taskTypes.remove(taskId);
        
        if (future != null && !future.isDone()) {
            long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
            System.out.println("PreciseCompletionTracker: Completing task " + taskId + " (duration: " + duration + "ms)");
            processStates.put(taskId, ProcessState.COMPLETED);
            future.complete(null);
        }
    }
    
    /**
     * Complete a task with an error
     */
    private void completeTaskWithError(String taskId, String reason) {
        CompletableFuture<Void> future = pendingTasks.remove(taskId);
        processStates.remove(taskId);
        taskStartTimes.remove(taskId);
        taskCompletionGraceStarts.remove(taskId);
        taskTypes.remove(taskId);

        if (future != null && !future.isDone()) {
            System.out.println("PreciseCompletionTracker: Completing task " + taskId + " with error: " + reason);
            processStates.put(taskId, ProcessState.FAILED);
            future.completeExceptionally(new RuntimeException(reason));
        }
    }

    private void failTaskGracefully(String taskId, String logMessage, String userMessage) {
        CompletableFuture<Void> future = pendingTasks.remove(taskId);
        processStates.remove(taskId);
        taskStartTimes.remove(taskId);
        taskCompletionGraceStarts.remove(taskId);
        taskTypes.remove(taskId);

        System.out.println("PreciseCompletionTracker: " + logMessage);
        notifyPlayer(userMessage);

        if (future != null && !future.isDone()) {
            future.complete(null);
        }
    }

    private void notifyPlayer(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(Text.literal(formatChatMessage(message)), false);
            }
        });
    }

    private String formatChatMessage(String message) {
        final String prefix = "\u00A74[\u00A7cPathmind\u00A74] \u00A77";
        if (message == null || message.isEmpty()) {
            return prefix.trim();
        }
        return prefix + message;
    }

    /**
     * Mark a task as completed from an external event (e.g. amount monitors).
     */
    public void markTaskCompleted(String taskId) {
        String resolvedTaskId = resolveTaskId(taskId);
        if (resolvedTaskId == null) {
            return;
        }

        CompletableFuture<Void> future = pendingTasks.remove(resolvedTaskId);
        if (future == null) {
            return;
        }

        processStates.remove(resolvedTaskId);
        Long startTime = taskStartTimes.remove(resolvedTaskId);
        taskCompletionGraceStarts.remove(resolvedTaskId);
        taskTypes.remove(resolvedTaskId);

        if (!future.isDone()) {
            long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
            System.out.println("PreciseCompletionTracker: Completing task " + resolvedTaskId + " from external signal (duration: " + duration + "ms)");
            future.complete(null);
        }
    }

    private String resolveTaskId(String taskIdOrType) {
        if (taskIdOrType == null) {
            return null;
        }
        if (pendingTasks.containsKey(taskIdOrType)) {
            return taskIdOrType;
        }
        for (Map.Entry<String, String> entry : taskTypes.entrySet()) {
            if (entry.getValue().equals(taskIdOrType)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Cancel all pending tasks
     */
    public void cancelAllTasks() {
        System.out.println("PreciseCompletionTracker: Canceling all pending tasks (" + pendingTasks.size() + " tasks)");

        for (String taskId : pendingTasks.keySet()) {
            CompletableFuture<Void> future = pendingTasks.get(taskId);
            if (future != null && !future.isDone()) {
                future.completeExceptionally(new RuntimeException("All tasks cancelled"));
            }
        }

        pendingTasks.clear();
        processStates.clear();
        taskStartTimes.clear();
        taskCompletionGraceStarts.clear();
        taskTypes.clear();
    }
    
    /**
     * Get the Baritone instance
     */
    private IBaritone getBaritone() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Exception e) {
            System.err.println("Failed to get Baritone instance: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get the number of pending tasks
     */
    public int getPendingTaskCount() {
        return pendingTasks.size();
    }
    
    /**
     * Check if a task is still pending
     */
    public boolean isTaskPending(String taskId) {
        return pendingTasks.containsKey(taskId);
    }
}
