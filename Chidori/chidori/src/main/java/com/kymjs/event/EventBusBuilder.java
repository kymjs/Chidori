/*
 * Copyright (C) 2014 Markus Junginger, greenrobot (http://greenrobot.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kymjs.event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 使用自定义的参数创建EventBus实例,也可以使用默认的build()创建实例
 */
public class EventBusBuilder {
    private final static ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    boolean logSubscriberExceptions = true;//监听异常日志
    boolean logNoSubscriberMessages = true; //如果没有订阅者,显示一个Log
    boolean sendSubscriberExceptionEvent = true; //发送监听到异常事件
    boolean sendNoSubscriberEvent = true; //如果没有订阅者,发送一条默认事件
    boolean throwSubscriberException; //如果失败则抛出异常
    boolean eventInheritance = true; //event的子类是否也能响应订阅者
    ExecutorService executorService = DEFAULT_EXECUTOR_SERVICE;
    List<Class<?>> skipMethodVerificationForClasses;

    EventBusBuilder() {
    }

    /**
     * Default: true
     */
    public EventBusBuilder logSubscriberExceptions(boolean logSubscriberExceptions) {
        this.logSubscriberExceptions = logSubscriberExceptions;
        return this;
    }

    /**
     * Default: true
     */
    public EventBusBuilder logNoSubscriberMessages(boolean logNoSubscriberMessages) {
        this.logNoSubscriberMessages = logNoSubscriberMessages;
        return this;
    }

    /**
     * Default: true
     */
    public EventBusBuilder sendSubscriberExceptionEvent(boolean sendSubscriberExceptionEvent) {
        this.sendSubscriberExceptionEvent = sendSubscriberExceptionEvent;
        return this;
    }

    /**
     * Default: true
     */
    public EventBusBuilder sendNoSubscriberEvent(boolean sendNoSubscriberEvent) {
        this.sendNoSubscriberEvent = sendNoSubscriberEvent;
        return this;
    }

    /**
     * Fails if an subscriber throws an exception (default: false).
     * <p/>
     * Tip:建议与BuildConfig.DEBUG配合使用,用于调试模式时显示崩溃日志。
     */
    public EventBusBuilder throwSubscriberException(boolean throwSubscriberException) {
        this.throwSubscriberException = throwSubscriberException;
        return this;
    }

    /**
     * 事件继承?event类型的子类也能响应订阅者
     * By default, EventBus considers the event class hierarchy (subscribers to super classes
     * will be notified).
     * Switching this feature off will improve posting of events. For simple event classes
     * extending Object directly,
     * we measured a speed up of 20% for event posting. For more complex event hierarchies, the
     * speed up should be >20%.
     */
    public EventBusBuilder eventInheritance(boolean eventInheritance) {
        this.eventInheritance = eventInheritance;
        return this;
    }


    /**
     * Provide a custom thread pool to EventBus used for async and background event delivery.
     * This is an advanced setting to that can break things: ensure the given ExecutorService 
     * won't get stuck to
     * avoid undefined behavior.
     */
    public EventBusBuilder executorService(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
    }

    /**
     * Method name verification is done for methods starting with onEvent to avoid typos; using
     * this method you can exclude subscriber classes from this check.
     * Also disables checks for method modifiers
     * (public, not static nor
     * abstract).
     */
    public EventBusBuilder skipMethodVerificationFor(Class<?> clazz) {
        if (skipMethodVerificationForClasses == null) {
            skipMethodVerificationForClasses = new ArrayList<Class<?>>();
        }
        skipMethodVerificationForClasses.add(clazz);
        return this;
    }

    /**
     * 根据参数创建对象,并赋值给EventBus.defaultInstance, 必须在默认的eventbus对象使用以前调用
     *
     * @throws EventBusException if there's already a default EventBus instance in place
     */
    public EventBus installDefaultEventBus() {
        synchronized (EventBus.class) {
            if (EventBus.defaultInstance != null) {
                throw new EventBusException("Default instance already exists." +
                        " It may be only set once before it's used the first time to ensure " +
                        "consistent behavior.");
            }
            EventBus.defaultInstance = build();
            return EventBus.defaultInstance;
        }
    }

    /**
     * 根据参数创建对象
     */
    public EventBus build() {
        return new EventBus(this);
    }

}
