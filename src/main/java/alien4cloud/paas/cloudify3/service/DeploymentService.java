package alien4cloud.paas.cloudify3.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify3.dao.BlueprintDAO;
import alien4cloud.paas.cloudify3.dao.DeploymentDAO;
import alien4cloud.paas.cloudify3.dao.ExecutionDAO;
import alien4cloud.paas.cloudify3.model.Blueprint;
import alien4cloud.paas.cloudify3.model.Deployment;
import alien4cloud.paas.cloudify3.model.Execution;
import alien4cloud.paas.cloudify3.model.ExecutionStatus;
import alien4cloud.paas.cloudify3.model.Workflow;
import alien4cloud.paas.cloudify3.service.model.AlienDeployment;
import alien4cloud.utils.FileUtil;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Handle deployment of the topology on cloudify 3
 *
 * @author Minh Khang VU
 */
@Component("cloudify-deployment-service")
@Slf4j
public class DeploymentService {

    @Resource
    private BlueprintService blueprintService;

    @Resource
    private BlueprintDAO blueprintDAO;

    @Resource
    private DeploymentDAO deploymentDAO;

    @Resource
    private ExecutionDAO executionDAO;

    private ListeningScheduledExecutorService scheduledExecutorService = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1));

    public ListenableFuture<Execution> deploy(final AlienDeployment alienDeployment) {
        log.info("Deploying app {} with id {}", alienDeployment.getDeploymentName(), alienDeployment.getDeploymentId());
        Path blueprintPath = blueprintService.generateBlueprint(alienDeployment);
        ListenableFuture<Blueprint> createdBlueprint = blueprintDAO.asyncCreate(alienDeployment.getDeploymentId(), blueprintPath.toString());
        AsyncFunction<Blueprint, Deployment> createDeploymentFunction = new AsyncFunction<Blueprint, Deployment>() {
            @Override
            public ListenableFuture<Deployment> apply(Blueprint input) throws Exception {
                return waitForDeploymentExecutionsFinish(deploymentDAO.asyncCreate(alienDeployment.getDeploymentId(), input.getId(),
                        Maps.<String, Object> newHashMap()));
            }
        };
        ListenableFuture<Deployment> createdDeployment = Futures.transform(createdBlueprint, createDeploymentFunction);
        AsyncFunction<Deployment, Execution> startExecutionFunction = new AsyncFunction<Deployment, Execution>() {
            @Override
            public ListenableFuture<Execution> apply(Deployment input) throws Exception {
                return waitForExecutionFinish(executionDAO.asyncStart(input.getId(), Workflow.INSTALL, null, false, false));
            }
        };
        return Futures.transform(createdDeployment, startExecutionFunction);
    }

    public ListenableFuture<?> undeploy(final String deploymentId) {
        log.info("Un-deploying app with id {}", deploymentId);
        try {
            FileUtil.delete(blueprintService.resolveBlueprintPath(deploymentId));
        } catch (IOException e) {
            log.warn("Unable to delete generated blueprint for deployment " + deploymentId, e);
        }
        ListenableFuture<Execution> startUninstall = waitForExecutionFinish(executionDAO.asyncStart(deploymentId, Workflow.UNINSTALL, null, false, false));
        AsyncFunction deleteDeploymentFunction = new AsyncFunction() {
            @Override
            public ListenableFuture apply(Object input) throws Exception {
                return deploymentDAO.asyncDelete(deploymentId);
            }
        };
        ListenableFuture deletedDeployment = Futures.transform(startUninstall, deleteDeploymentFunction);
        // TODO Due to bug index not refreshed of cloudify 3.1 (will be corrected in 3.2). We schedule the delete of blueprint 2 seconds after the delete of
        // deployment
        AsyncFunction<?, ?> deleteBlueprintFunction = new AsyncFunction() {
            @Override
            public ListenableFuture<?> apply(Object input) throws Exception {
                ListenableFuture<?> scheduledDeleteBlueprint = Futures.dereference(scheduledExecutorService.schedule(
                        new Callable<ListenableFuture<?>>() {
                            @Override
                            public ListenableFuture<?> call() throws Exception {
                                return blueprintDAO.asyncDelete(deploymentId);
                            }
                        }, 2, TimeUnit.SECONDS));
                return scheduledDeleteBlueprint;
            }
        };
        return Futures.transform(deletedDeployment, deleteBlueprintFunction);
    }

    private ListenableFuture<Execution> waitForExecutionFinish(final ListenableFuture<Execution> futureExecution) {
        AsyncFunction<Execution, Execution> waitFunc = new AsyncFunction<Execution, Execution>() {
            @Override
            public ListenableFuture<Execution> apply(final Execution execution) throws Exception {
                if (ExecutionStatus.isTerminated(execution.getStatus())) {
                    log.info("Execution {} for workflow {} has finished with status {}", execution.getId(), execution.getWorkflowId(), execution.getStatus());
                    return futureExecution;
                } else {
                    // If it's not finished, schedule another poll in 2 seconds
                    ListenableFuture<Execution> newFutureExecution = Futures.dereference(scheduledExecutorService.schedule(
                            new Callable<ListenableFuture<Execution>>() {
                                @Override
                                public ListenableFuture<Execution> call() throws Exception {
                                    return executionDAO.asyncRead(execution.getId());
                                }
                            }, 2, TimeUnit.SECONDS));
                    return waitForExecutionFinish(newFutureExecution);
                }
            }
        };
        return Futures.transform(futureExecution, waitFunc);
    }

    private ListenableFuture<Execution[]> waitForDeploymentExecutionsFinish(final Deployment deployment, final ListenableFuture<Execution[]> futureExecutions) {
        AsyncFunction<Execution[], Execution[]> waitFunc = new AsyncFunction<Execution[], Execution[]>() {
            @Override
            public ListenableFuture<Execution[]> apply(final Execution[] executions) throws Exception {
                boolean allExecutionFinished = true;
                for (Execution execution : executions) {
                    if (!ExecutionStatus.isTerminated(execution.getStatus())) {
                        allExecutionFinished = false;
                        break;
                    }
                }
                if (allExecutionFinished) {
                    return futureExecutions;
                } else {
                    // If it's not finished, schedule another poll in 2 seconds
                    ListenableFuture<Execution[]> newFutureExecutions = Futures.dereference(scheduledExecutorService.schedule(
                            new Callable<ListenableFuture<Execution[]>>() {
                                @Override
                                public ListenableFuture<Execution[]> call() throws Exception {
                                    return executionDAO.asyncList(deployment.getId());
                                }
                            }, 2, TimeUnit.SECONDS));
                    return waitForDeploymentExecutionsFinish(deployment, newFutureExecutions);
                }
            }
        };
        return Futures.transform(futureExecutions, waitFunc);
    }

    private ListenableFuture<Deployment> waitForDeploymentExecutionsFinish(final ListenableFuture<Deployment> futureDeployment) {
        AsyncFunction<Deployment, Deployment> waitFunc = new AsyncFunction<Deployment, Deployment>() {
            @Override
            public ListenableFuture<Deployment> apply(final Deployment deployment) throws Exception {
                final ListenableFuture<Execution[]> futureExecutions = waitForDeploymentExecutionsFinish(deployment, executionDAO.asyncList(deployment.getId()));
                Function<Execution[], Deployment> adaptFunc = new Function<Execution[], Deployment>() {
                    @Override
                    public Deployment apply(Execution[] input) {
                        log.info("All execution has finished for deployment {}", deployment.getId());
                        return deployment;
                    }
                };
                return Futures.transform(futureExecutions, adaptFunc);
            }
        };
        return Futures.transform(futureDeployment, waitFunc);
    }
}
