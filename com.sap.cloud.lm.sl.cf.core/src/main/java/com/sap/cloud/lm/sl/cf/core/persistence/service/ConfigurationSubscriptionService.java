package com.sap.cloud.lm.sl.cf.core.persistence.service;

import static java.text.MessageFormat.format;

import java.util.Map;

import javax.persistence.EntityManagerFactory;

import org.apache.commons.lang3.ObjectUtils;
import org.cloudfoundry.multiapps.common.ConflictException;
import org.cloudfoundry.multiapps.common.NotFoundException;
import org.cloudfoundry.multiapps.common.SLException;
import org.cloudfoundry.multiapps.common.util.JsonUtil;

import com.sap.cloud.lm.sl.cf.core.Messages;
import com.sap.cloud.lm.sl.cf.core.model.ConfigurationFilter;
import com.sap.cloud.lm.sl.cf.core.model.ConfigurationSubscription;
import com.sap.cloud.lm.sl.cf.core.model.ConfigurationSubscription.ModuleDto;
import com.sap.cloud.lm.sl.cf.core.model.ConfigurationSubscription.ResourceDto;
import com.sap.cloud.lm.sl.cf.core.persistence.dto.ConfigurationSubscriptionDto;
import com.sap.cloud.lm.sl.cf.core.persistence.query.ConfigurationSubscriptionQuery;
import com.sap.cloud.lm.sl.cf.core.persistence.query.impl.ConfigurationSubscriptionQueryImpl;

public class ConfigurationSubscriptionService extends PersistenceService<ConfigurationSubscription, ConfigurationSubscriptionDto, Long> {

    protected ConfigurationSubscriptionMapper subscriptionMapper;

    public ConfigurationSubscriptionService(EntityManagerFactory entityManagerFactory, ConfigurationSubscriptionMapper subscriptionMapper) {
        super(entityManagerFactory);
        this.subscriptionMapper = subscriptionMapper;
    }

    public ConfigurationSubscriptionQuery createQuery() {
        return new ConfigurationSubscriptionQueryImpl(createEntityManager(), subscriptionMapper);
    }

    @Override
    protected ConfigurationSubscriptionDto merge(ConfigurationSubscriptionDto existingSubscription,
                                                 ConfigurationSubscriptionDto newSubscription) {
        super.merge(existingSubscription, newSubscription);
        String mtaId = ObjectUtils.firstNonNull(newSubscription.getMtaId(), existingSubscription.getMtaId());
        String appName = ObjectUtils.firstNonNull(newSubscription.getAppName(), existingSubscription.getAppName());
        String spaceId = ObjectUtils.firstNonNull(newSubscription.getSpaceId(), existingSubscription.getSpaceId());
        String filter = ObjectUtils.firstNonNull(newSubscription.getFilter(), existingSubscription.getFilter());
        String moduleContent = ObjectUtils.firstNonNull(newSubscription.getModuleContent(), existingSubscription.getModuleContent());
        String resourceProperties = ObjectUtils.firstNonNull(newSubscription.getResourceProperties(),
                                                             existingSubscription.getResourceProperties());
        String resourceName = ObjectUtils.firstNonNull(newSubscription.getResourceName(), existingSubscription.getResourceName());
        String moduleId = ObjectUtils.firstNonNull(newSubscription.getModuleId(), existingSubscription.getModuleId());
        String resourceId = ObjectUtils.firstNonNull(newSubscription.getResourceId(), existingSubscription.getResourceId());
        return ConfigurationSubscriptionDto.builder()
                                           .id(newSubscription.getPrimaryKey())
                                           .mtaId(mtaId)
                                           .spaceId(spaceId)
                                           .appName(appName)
                                           .filter(filter)
                                           .module(moduleContent)
                                           .resourceName(resourceName)
                                           .resourceProperties(resourceProperties)
                                           .moduleId(moduleId)
                                           .resourceId(resourceId)
                                           .build();
    }

    @Override
    protected PersistenceObjectMapper<ConfigurationSubscription, ConfigurationSubscriptionDto> getPersistenceObjectMapper() {
        return subscriptionMapper;
    }

    @Override
    protected void onEntityConflict(ConfigurationSubscriptionDto subscription, Throwable t) {
        throw new ConflictException(t,
                                    Messages.CONFIGURATION_SUBSCRIPTION_ALREADY_EXISTS,
                                    subscription.getMtaId(),
                                    subscription.getAppName(),
                                    subscription.getResourceName(),
                                    subscription.getSpaceId());
    }

    @Override
    protected void onEntityNotFound(Long id) {
        throw new NotFoundException(Messages.CONFIGURATION_SUBSCRIPTION_NOT_FOUND, id);
    }

    public static class ConfigurationSubscriptionMapper
        implements PersistenceObjectMapper<ConfigurationSubscription, ConfigurationSubscriptionDto> {

        @Override
        public ConfigurationSubscription fromDto(ConfigurationSubscriptionDto dto) {
            try {
                ConfigurationFilter parsedFilter = JsonUtil.fromJson(dto.getFilter(), ConfigurationFilter.class);
                Map<String, Object> parsedResourceProperties = JsonUtil.convertJsonToMap(dto.getResourceProperties());
                ResourceDto resourceDto = new ResourceDto(dto.getResourceName(), parsedResourceProperties);
                ModuleDto moduleDto = JsonUtil.fromJson(dto.getModuleContent(), ModuleDto.class);
                String moduleId = dto.getModuleId();
                String resourceId = dto.getResourceId();
                return new ConfigurationSubscription(dto.getPrimaryKey(),
                                                     dto.getMtaId(),
                                                     dto.getSpaceId(),
                                                     dto.getAppName(),
                                                     parsedFilter,
                                                     moduleDto,
                                                     resourceDto,
                                                     moduleId,
                                                     resourceId);
            } catch (SLException e) {
                throw new IllegalStateException(format(Messages.UNABLE_TO_PARSE_SUBSCRIPTION, e.getMessage()), e);
            }
        }

        @Override
        public ConfigurationSubscriptionDto toDto(ConfigurationSubscription subscription) {
            long id = subscription.getId();
            String filter = null;
            if (subscription.getFilter() != null) {
                filter = JsonUtil.toJson(subscription.getFilter(), false);
            }
            ResourceDto resourceDto = subscription.getResourceDto();
            ModuleDto moduleDto = subscription.getModuleDto();
            String resourceProperties = null;
            String resourceName = null;
            if (resourceDto != null) {
                resourceProperties = JsonUtil.toJson(resourceDto.getProperties(), false);
                resourceName = resourceDto.getName();
            }
            String module = null;
            if (moduleDto != null) {
                module = JsonUtil.toJson(moduleDto, false);
            }
            String appName = subscription.getAppName();
            String spaceId = subscription.getSpaceId();
            String mtaId = subscription.getMtaId();
            String moduleId = subscription.getModuleId();
            String resourceId = subscription.getResourceId();
            return ConfigurationSubscriptionDto.builder()
                                               .id(id)
                                               .mtaId(mtaId)
                                               .spaceId(spaceId)
                                               .appName(appName)
                                               .filter(filter)
                                               .module(module)
                                               .resourceName(resourceName)
                                               .resourceProperties(resourceProperties)
                                               .moduleId(moduleId)
                                               .resourceId(resourceId)
                                               .build();
        }

    }
}