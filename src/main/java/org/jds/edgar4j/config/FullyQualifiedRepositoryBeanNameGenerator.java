package org.jds.edgar4j.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.util.StringUtils;

public class FullyQualifiedRepositoryBeanNameGenerator implements BeanNameGenerator {

    @Override
    public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
        String repositoryInterfaceName = extractRepositoryInterfaceName(definition);
        if (StringUtils.hasText(repositoryInterfaceName)) {
            return repositoryInterfaceName;
        }
        String beanClassName = definition.getBeanClassName();
        if (StringUtils.hasText(beanClassName)) {
            return beanClassName;
        }
        String resourceDescription = definition.getResourceDescription();
        return StringUtils.hasText(resourceDescription) ? resourceDescription : ("mongoRepository#" + definition.hashCode());
    }

    private String extractRepositoryInterfaceName(BeanDefinition definition) {
        ConstructorArgumentValues constructorArgumentValues = definition.getConstructorArgumentValues();
        if (constructorArgumentValues == null) {
            return null;
        }
        ValueHolder holder = constructorArgumentValues.getArgumentValue(0, null);
        if (holder == null) {
            return null;
        }
        Object value = holder.getValue();
        if (value instanceof Class<?> repositoryInterface) {
            return repositoryInterface.getName();
        }
        if (value instanceof String repositoryInterfaceName) {
            return repositoryInterfaceName;
        }
        if (value instanceof TypedStringValue typedStringValue) {
            return typedStringValue.getValue();
        }
        return null;
    }
}
