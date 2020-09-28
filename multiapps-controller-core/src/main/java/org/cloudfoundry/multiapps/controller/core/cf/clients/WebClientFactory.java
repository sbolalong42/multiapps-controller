package org.cloudfoundry.multiapps.controller.core.cf.clients;

import javax.inject.Named;

import org.cloudfoundry.client.lib.CloudControllerClient;
import org.cloudfoundry.client.lib.util.RestUtil;
import org.springframework.web.reactive.function.client.WebClient;

@Named
public class WebClientFactory {

    public WebClient getWebClient(CloudControllerClient client) {
        WebClient.Builder webClientBuilder = new RestUtil().createWebClient(false)
                                                           .mutate();
        webClientBuilder.defaultHeaders(httpHeaders -> httpHeaders.setBearerAuth(computeAuthorizationToken(client)));
        return webClientBuilder.build();
    }

    private String computeAuthorizationToken(CloudControllerClient client) {
        return client.login()
                     .toString();
    }

}
