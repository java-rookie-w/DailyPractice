package org.wang.interviewnotes.spring;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

public class GetBean {
    public static void main(String[] args) {
        ConfigurableListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.getBean("Student");
    }
}
