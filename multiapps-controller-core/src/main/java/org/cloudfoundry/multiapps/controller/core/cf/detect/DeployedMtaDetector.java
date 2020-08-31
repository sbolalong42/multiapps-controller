package org.cloudfoundry.multiapps.controller.core.cf.detect;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;

import org.cloudfoundry.client.lib.CloudControllerClient;
import org.cloudfoundry.client.lib.domain.CloudEntity;
import org.cloudfoundry.multiapps.controller.core.cf.metadata.MtaMetadataLabels;
import org.cloudfoundry.multiapps.controller.core.cf.metadata.criteria.MtaMetadataCriteria;
import org.cloudfoundry.multiapps.controller.core.cf.metadata.criteria.MtaMetadataCriteriaBuilder;
import org.cloudfoundry.multiapps.controller.core.cf.metadata.entity.processor.MtaMetadataEntityAggregator;
import org.cloudfoundry.multiapps.controller.core.cf.metadata.entity.processor.MtaMetadataEntityCollector;
import org.cloudfoundry.multiapps.controller.core.cf.metadata.util.MtaMetadataUtil;
import org.cloudfoundry.multiapps.controller.core.model.DeployedMta;

@Named
public class DeployedMtaDetector {

    @Inject
    private List<MtaMetadataEntityCollector<?>> mtaMetadataEntityCollectors;

    @Inject
    private MtaMetadataEntityAggregator mtaMetadataEntityAggregator;

    public List<DeployedMta> detectDeployedMtas(CloudControllerClient client) {
        MtaMetadataCriteria allMtasCriteria = MtaMetadataCriteriaBuilder.builder()
                                                                        .label(MtaMetadataLabels.MTA_ID)
                                                                        .exists()
                                                                        .build();

        return getDeployedMtasByMetadataSelectionCriteria(allMtasCriteria, client);
    }

    public List<DeployedMta> detectDeployedMtasWithoutNamespace(CloudControllerClient client) {
        MtaMetadataCriteria mtasWithoutNamespaceCriteria = MtaMetadataCriteriaBuilder.builder()
                                                                                     .label(MtaMetadataLabels.MTA_ID)
                                                                                     .exists()
                                                                                     .and()
                                                                                     .label(MtaMetadataLabels.MTA_NAMESPACE)
                                                                                     .doesNotExist()
                                                                                     .build();

        return getDeployedMtasByMetadataSelectionCriteria(mtasWithoutNamespaceCriteria, client);
    }

    public List<DeployedMta> detectDeployedMtasByName(String mtaName, CloudControllerClient client) {
        MtaMetadataCriteria selectionCriteria = MtaMetadataCriteriaBuilder.builder()
                                                                          .label(MtaMetadataLabels.MTA_ID)
                                                                          .hasValue(MtaMetadataUtil.getHashedLabel(mtaName))
                                                                          .build();

        return getDeployedMtasByMetadataSelectionCriteria(selectionCriteria, client);
    }

    public List<DeployedMta> detectDeployedMtasByNamespace(String mtaNamespace, CloudControllerClient client) {
        MtaMetadataCriteria selectionCriteria = MtaMetadataCriteriaBuilder.builder()
                                                                          .label(MtaMetadataLabels.MTA_ID)
                                                                          .exists()
                                                                          .and()
                                                                          .label(MtaMetadataLabels.MTA_NAMESPACE)
                                                                          .hasValueOrIsntPresent(MtaMetadataUtil.getHashedLabel(mtaNamespace))
                                                                          .build();

        return getDeployedMtasByMetadataSelectionCriteria(selectionCriteria, client);
    }

    public Optional<DeployedMta> detectDeployedMtaByNameAndNamespace(String mtaName, String mtaNamespace, CloudControllerClient client,
                                                                     boolean envDetectionEnabled) {
        MtaMetadataCriteria selectionCriteria = MtaMetadataCriteriaBuilder.builder()
                                                                          .label(MtaMetadataLabels.MTA_ID)
                                                                          .hasValue(MtaMetadataUtil.getHashedLabel(mtaName))
                                                                          .and()
                                                                          .label(MtaMetadataLabels.MTA_NAMESPACE)
                                                                          .hasValueOrIsntPresent(MtaMetadataUtil.getHashedLabel(mtaNamespace))
                                                                          .build();

        return getDeployedMtasByMetadataSelectionCriteria(selectionCriteria, client).stream()
                                                                                    .findFirst();
    }

    private List<DeployedMta> getDeployedMtasByMetadataSelectionCriteria(MtaMetadataCriteria criteria, CloudControllerClient client) {
        List<CloudEntity> mtaMetadataEntities = mtaMetadataEntityCollectors.stream()
                                                                           .map(collector -> collector.collect(client, criteria))
                                                                           .flatMap(List::stream)
                                                                           .collect(Collectors.toList());

        return mtaMetadataEntityAggregator.aggregate(mtaMetadataEntities);
    }

}
