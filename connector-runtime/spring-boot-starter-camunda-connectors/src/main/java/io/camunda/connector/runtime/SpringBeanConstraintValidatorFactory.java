package io.camunda.connector.runtime;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

public class SpringBeanConstraintValidatorFactory implements ConstraintValidatorFactory {
  private final AutowireCapableBeanFactory beanFactory;

  public SpringBeanConstraintValidatorFactory(AutowireCapableBeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
    // Try to find an existing bean of this type first
    try {
      return beanFactory.getBean(key);
    } catch (NoSuchBeanDefinitionException e) {
      // Fall back to creating a new autowired instance
      return beanFactory.createBean(key);
    }
  }

  @Override
  public void releaseInstance(ConstraintValidator<?, ?> instance) {}
}
