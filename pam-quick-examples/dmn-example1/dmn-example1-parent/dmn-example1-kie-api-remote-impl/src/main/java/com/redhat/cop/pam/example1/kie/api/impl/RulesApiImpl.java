package com.redhat.cop.pam.example1.kie.api.impl;

import com.redhat.cop.pam.example1.CanOpenAccountResults;
import com.redhat.cop.pam.example1.Customer;
import com.redhat.cop.pam.example1.kie.api.RulesApi;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNDecisionResult;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNResult;
import org.kie.server.api.model.KieContainerResource;
import org.kie.server.api.model.ReleaseId;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.client.DMNServicesClient;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class RulesApiImpl implements RulesApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(RulesApiImpl.class);

    private static final String URL = System.getProperty("com.redhat.cop.pam.kieserver_url");

    private static final String USER = System.getProperty("com.redhat.cop.pam.kieserver_user");

    private static final String PASSWORD = System.getProperty("com.redhat.cop.pam.kieserver_password");

    private static final String NAMESPACE = "https://pam.cop.redhat.com/dmn-example-1/dmn";

    private static final String MODEL_NAME = "customer-can-open-account";

    private static final String CONTAINER_ID = "com.redhat.cop.pam:dmn-example1-kjar:1.0";

    private KieServicesClient kieServicesClient;

    @PostConstruct
    public void init(){
        final KieServicesConfiguration config = KieServicesFactory.newRestConfiguration(URL, USER, PASSWORD);
        final Set<Class<?>> extraClasses = new HashSet<>();
        extraClasses.add(Customer.class);
        config.addExtraClasses(extraClasses);
        config.setTimeout(100000l);
        System.out.println("url: " + URL);
        System.out.println("user: " + USER);
        System.out.println("password: " + PASSWORD);
        kieServicesClient = KieServicesFactory.newKieServicesClient(config);
        final String[] gav = CONTAINER_ID.split(":");
        final KieContainerResource containerResource = new KieContainerResource(CONTAINER_ID, new ReleaseId(gav[0], gav[1], gav[2]));
        kieServicesClient.createContainer(CONTAINER_ID, containerResource);
    }

    @PreDestroy
    public void preDestroy(){
        kieServicesClient.disposeContainer(CONTAINER_ID);
    }

    @Override
    public CanOpenAccountResults canOpenAccount(final Customer customer) {
        final DMNServicesClient dmnClient = kieServicesClient.getServicesClient(DMNServicesClient.class );
        final DMNContext dmnContext = dmnClient.newContext();
        dmnContext.set("customer", customer);
        final ServiceResponse<DMNResult> serviceResponse = dmnClient.evaluateAll(CONTAINER_ID, NAMESPACE, MODEL_NAME, dmnContext);
        final DMNResult dmnResult = serviceResponse.getResult();
        return extractResult(dmnResult);
    }

    private CanOpenAccountResults extractResult(final DMNResult dmnResult) {
        final DMNDecisionResult decisionResults = dmnResult.getDecisionResultByName("can open account");
        if(decisionResults.hasErrors()){
            final String errors = dmnResult.getMessages(DMNMessage.Severity.ERROR).stream()
                    .map(message -> message.toString())
                    .collect(Collectors.joining(", "));
            throw new RuntimeException("DMN Error messages {" + errors + "}");
        }
        return CanOpenAccountResults.valueOf(String.valueOf(decisionResults.getResult()));
    }

}