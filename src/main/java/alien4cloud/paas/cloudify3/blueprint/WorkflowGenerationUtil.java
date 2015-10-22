package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.wf.AbstractStep;
import alien4cloud.paas.wf.DelegateWorkflowActivity;
import alien4cloud.paas.wf.NodeActivityStep;
import alien4cloud.paas.wf.OperationCallActivity;
import alien4cloud.paas.wf.SetStateActivity;
import alien4cloud.paas.wf.Workflow;

@Slf4j
public class WorkflowGenerationUtil extends AbstractGenerationUtil {

    public WorkflowGenerationUtil(MappingConfiguration mappingConfiguration, CloudifyDeployment alienDeployment, Path recipePath,
            PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }

    public boolean isSetStateTask(AbstractStep step) {
        return step instanceof NodeActivityStep && ((NodeActivityStep) step).getActivity() instanceof SetStateActivity;
    }

    public boolean isOperationExecutionTask(AbstractStep step) {
        return step instanceof NodeActivityStep && ((NodeActivityStep) step).getActivity() instanceof OperationCallActivity;
    }

    public AbstractStep getWorkflowStep(Workflow wf, String stepName) {
        return wf.getSteps().get(stepName);
    }

    public boolean isDelegateActivityStep(AbstractStep step) {
        return step instanceof NodeActivityStep && ((NodeActivityStep) step).getActivity() instanceof DelegateWorkflowActivity;
    }

}
