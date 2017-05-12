/*
 * Copyright (C) 2012 Markus Junginger, greenrobot (http://greenrobot.de)
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

import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;

import com.kymjs.event.remote.ApplicationHolder;
import com.kymjs.event.remote.ChidoriClient;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

/**
 * EventBus is a central publish/subscribe event system for Android. Events are posted
 * ({@link #post(Object)}) to the
 * bus, which delivers it to subscribers that have a matching handler method for the event type.
 * To receive events,
 * subscribers must register themselves to the bus using {@link #register(Object)}. Once registered,
 * subscribers receive events until {@link #unregister(Object)} is called. By convention, event
 * handling methods must
 * be named "onEvent", be public, return nothing (void), and have exactly one parameter (the event).
 *
 * @author Markus Junginger, greenrobot
 */
public class EventBus {

    /**
     * Log tag, apps may override it.
     */
    public static String TAG = "Event";

    static volatile EventBus defaultInstance;

    private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();

    //key:事件类型,value:由事件的所有父类,父类的接口,父类接口的父类 组成的集合
    private static final Map<Class<?>, List<Class<?>>> eventTypesCache = new HashMap<Class<?>,
            List<Class<?>>>();

    //key:订阅的事件,value:订阅这个事件的所有订阅者集合
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
    //当前订阅者订阅了哪些事件
    //key:订阅者对象,value:这个订阅者订阅的事件集合
    private final Map<Object, List<Class<?>>> typesBySubscriber;

    private final Map<Class<?>, Object> stickyEvents;

    private final ThreadLocal<PostingThreadState> currentPostingThreadState = new
            ThreadLocal<PostingThreadState>() {
                @Override
                protected PostingThreadState initialValue() {
                    return new PostingThreadState();
                }
            };

    private final HandlerPoster mainThreadPoster; //前台发送者
    private final BackgroundPoster backgroundPoster; //后台发送者
    private final AsyncPoster asyncPoster;   //后台发送者(只让队列第一个待订阅者去响应)
    private final SubscriberMethodFinder subscriberMethodFinder;  //订阅者方法查询
    private final ExecutorService executorService; //线程池执行器

    //同EventBusBuilder中的属性
    private final boolean throwSubscriberException;
    private final boolean logSubscriberExceptions;
    private final boolean logNoSubscriberMessages; //如果某个事件没有订阅者,是否显示一条log
    private final boolean sendSubscriberExceptionEvent;
    private final boolean sendNoSubscriberEvent; //如果某个事件没有订阅者,是否发送一个特定的事件
    private final boolean eventInheritance;//event的子类是否也能响应订阅者

    /**
     * Convenience singleton for apps using a process-wide EventBus instance.
     */
    public static EventBus getDefault() {
        return getDefault(DEFAULT_BUILDER);
    }

    //不给设置
    public static EventBus getDefault(EventBusBuilder builder) {
        if (defaultInstance == null) {
            synchronized (EventBus.class) {
                if (defaultInstance == null) {
                    defaultInstance = new EventBus(builder);
                }
            }
        }
        return defaultInstance;
    }

    /**
     * Creates a new EventBus instance; each instance is a separate scope in which events are
     * delivered. To use a
     * central bus, consider {@link #getDefault()}.
     */
    private EventBus() {
        this(DEFAULT_BUILDER);
    }

    EventBus(EventBusBuilder builder) {
        subscriptionsByEventType = new HashMap<Class<?>, CopyOnWriteArrayList<Subscription>>();
        typesBySubscriber = new HashMap<Object, List<Class<?>>>();
        stickyEvents = new ConcurrentHashMap<Class<?>, Object>();
        mainThreadPoster = new HandlerPoster(this, Looper.getMainLooper(), 10);
        backgroundPoster = new BackgroundPoster(this);
        asyncPoster = new AsyncPoster(this);
        subscriberMethodFinder = new SubscriberMethodFinder(builder
                .skipMethodVerificationForClasses);
        logSubscriberExceptions = builder.logSubscriberExceptions;
        logNoSubscriberMessages = builder.logNoSubscriberMessages;
        sendSubscriberExceptionEvent = builder.sendSubscriberExceptionEvent;
        sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
        throwSubscriberException = builder.throwSubscriberException;
        eventInheritance = builder.eventInheritance;
        executorService = builder.executorService;
    }

    /**
     * @param subscriber 订阅者对象
     */
    public void register(Object subscriber) {
        register(subscriber, false, 0);
    }

    /**
     * @param subscriber 订阅者对象
     * @param priority   优先级
     */
    public void register(Object subscriber, int priority) {
        register(subscriber, false, priority);
    }

    /**
     * @param subscriber 订阅者对象
     */
    public void registerSticky(Object subscriber) {
        register(subscriber, true, 0);
    }

    /**
     * @param subscriber 订阅者对象
     * @param priority   优先级
     */
    public void registerSticky(Object subscriber, int priority) {
        register(subscriber, true, priority);
    }

    /**
     * @param subscriber 订阅者对象
     * @param sticky     是否有序
     * @param priority   优先级
     */
    private synchronized void register(Object subscriber, boolean sticky, int priority) {
        List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods
                (subscriber.getClass());
        for (SubscriberMethod subscriberMethod : subscriberMethods) {
            subscribe(subscriber, subscriberMethod, sticky, priority);
        }
    }

    /**
     * 必须在同步代码块调用
     *
     * @param subscriber       订阅者对象
     * @param subscriberMethod 响应的方法名
     * @param sticky           是否有序
     * @param priority         优先级
     */
    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod, boolean sticky,
                           int priority) {
        //根据传入的响应方法名获取到响应事件(参数类型)
        Class<?> eventType = subscriberMethod.eventType;
        Subscription newSubscription = new Subscription(subscriber, subscriberMethod, priority);
        //通过响应事件作为key,并取得这个事件类型将会响应的全部订阅者
        //没个订阅者至少会订阅一个事件,多个订阅者可能订阅同一个事件(多对多)
        //key:订阅的事件,value:订阅这个事件的所有订阅者集合
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions == null) {
            subscriptions = new CopyOnWriteArrayList<Subscription>();
            subscriptionsByEventType.put(eventType, subscriptions);
        } else {
            if (subscriptions.contains(newSubscription)) {
                throw new EventBusException("Subscriber " + subscriber.getClass() + " already " +
                        "registered to event " + eventType);
            }
        }

        //根据优先级插入到订阅者集合中
        int size = subscriptions.size();
        for (int i = 0; i <= size; i++) {
            if (i == size || newSubscription.priority > subscriptions.get(i).priority) {
                subscriptions.add(i, newSubscription);
                break;
            }
        }

        //当前订阅者订阅了哪些事件
        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<Class<?>>();
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        subscribedEvents.add(eventType);

        if (sticky) {
            if (eventInheritance) {
                // 注：遍历所有的事件可能是低效的，有很多黏事件，因此数据结构应该改变，以便更有效的查找
                // （例如额外的地图存储超类的子类：类 - >列表<类>）。
                Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
                for (Map.Entry<Class<?>, Object> entry : entries) {
                    Class<?> candidateEventType = entry.getKey();
                    //如果eventtype是candidateEventType同一个类或是其子类
                    if (eventType.isAssignableFrom(candidateEventType)) {
                        Object stickyEvent = entry.getValue();
                        checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                    }
                }
            } else {
                Object stickyEvent = stickyEvents.get(eventType);
                checkPostStickyEventToSubscription(newSubscription, stickyEvent);
            }
        }
    }

    private void checkPostStickyEventToSubscription(Subscription newSubscription, Object
            stickyEvent) {
        if (stickyEvent != null) {
            // If the subscriber is trying to abort the event, it will fail (event is not tracked
            // in posting state)
            // --> Strange corner case, which we don't take care of here.
            postToSubscription(newSubscription, stickyEvent, Looper.getMainLooper() == Looper
                    .myLooper());
        }
    }

    public synchronized boolean isRegistered(Object subscriber) {
        return typesBySubscriber.containsKey(subscriber);
    }

    /**
     * Unregisters the given subscriber from all event classes.
     */
    public synchronized void unregister(Object subscriber) {
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            for (Class<?> eventType : subscribedTypes) {
                //取消注册subscriber对eventType事件的响应
                unsubscribeByEventType(subscriber, eventType);
            }
            //当subscriber对所有事件都不响应以后,移除订阅者
            typesBySubscriber.remove(subscriber);
        } else {
            Log.w(TAG, "Subscriber to unregister was not registered before: " + subscriber
                    .getClass());
        }
    }

    /**
     * 取消注册订阅者对参数eventType的响应
     * 注:只更新subscriptionsByEventType，不更新typesBySubscriber！调用者必须手动更新typesBySubscriber。
     */
    private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
        List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions != null) {
            int size = subscriptions.size();
            for (int i = 0; i < size; i++) {
                Subscription subscription = subscriptions.get(i);
                if (subscription.subscriber == subscriber) {
                    subscription.active = false;
                    subscriptions.remove(i);
                    i--;
                    size--;
                }
            }
        }
    }

    /**
     * Posts the given event to the event bus.
     */
    public void post(Object event) {
        PostingThreadState postingState = currentPostingThreadState.get();
        List<Object> eventQueue = postingState.eventQueue;
        eventQueue.add(event);

        if (!postingState.isPosting) {
            postingState.isMainThread = Looper.getMainLooper() == Looper.myLooper();
            postingState.isPosting = true;
            if (postingState.canceled) {
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            try {
                while (!eventQueue.isEmpty()) {
                    postSingleEvent(eventQueue.remove(0), postingState);
                }
            } finally {
                postingState.isPosting = false;
                postingState.isMainThread = false;
            }
        }
    }

    /**
     * Called from a subscriber's event handling method, further event delivery will be canceled.
     * Subsequent
     * subscribers
     * won't receive the event. Events are usually canceled by higher priority subscribers (see
     * {@link #register(Object, int)}). Canceling is restricted to event handling methods running
     * in posting thread
     * {@link ThreadMode#PostThread}.
     */
    public void cancelEventDelivery(Object event) {
        PostingThreadState postingState = currentPostingThreadState.get();
        if (!postingState.isPosting) {
            throw new EventBusException(
                    "This method may only be called from inside event handling methods on the " +
                            "posting thread");
        } else if (event == null) {
            throw new EventBusException("Event may not be null");
        } else if (postingState.event != event) {
            throw new EventBusException("Only the currently handled event may be aborted");
        } else if (postingState.subscription.subscriberMethod.threadMode != ThreadMode.PostThread) {
            throw new EventBusException(" event handlers may only abort the incoming event");
        }

        postingState.canceled = true;
    }

    /**
     * Posts the given event to the event bus and holds on to the event (because it is sticky).
     * The most recent sticky
     * event of an event's type is kept in memory for future access. This can be
     * {@link #registerSticky(Object)} or
     * {@link #getStickyEvent(Class)}.
     */
    public void postSticky(Object event) {
        synchronized (stickyEvents) {
            stickyEvents.put(event.getClass(), event);
        }
        // Should be posted after it is putted, in case the subscriber wants to remove immediately
        post(event);
    }

    /**
     * Gets the most recent sticky event for the given type.
     *
     * @see #postSticky(Object)
     */
    public <T> T getStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.get(eventType));
        }
    }

    /**
     * Remove and gets the recent sticky event for the given event type.
     *
     * @see #postSticky(Object)
     */
    public <T> T removeStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.remove(eventType));
        }
    }

    /**
     * Removes the sticky event if it equals to the given event.
     *
     * @return true if the events matched and the sticky event was removed.
     */
    public boolean removeStickyEvent(Object event) {
        synchronized (stickyEvents) {
            Class<?> eventType = event.getClass();
            Object existingEvent = stickyEvents.get(eventType);
            if (event.equals(existingEvent)) {
                stickyEvents.remove(eventType);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Removes all sticky events.
     */
    public void removeAllStickyEvents() {
        synchronized (stickyEvents) {
            stickyEvents.clear();
        }
    }

    public boolean hasSubscriberForEvent(Class<?> eventClass) {
        List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
        if (eventTypes != null) {
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                CopyOnWriteArrayList<Subscription> subscriptions;
                synchronized (this) {
                    subscriptions = subscriptionsByEventType.get(clazz);
                }
                if (subscriptions != null && !subscriptions.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 发送一次事件
     *
     * @param event
     * @param postingState
     * @throws Error
     */
    private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
        Class<?> eventClass = event.getClass();
        boolean subscriptionFound = false;
        if (eventInheritance) {
            //获取到eventClass所有父类的集合
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                //左或右只要有一个为真则为真,并赋值给左
                subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);
            }
        } else {
            subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
        }
        if (!subscriptionFound) {
            if (logNoSubscriberMessages) {
                Log.d(TAG, "No subscribers registered for event " + eventClass);
            }

            //参考sendNoSubscriberEvent注释
            if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class &&
                    eventClass != SubscriberExceptionEvent.class) {
                post(new NoSubscriberEvent(this, event));
            }
        }
    }

    /**
     * 找出所有订阅了eventClass事件的订阅者,并回调订阅者的响应方法
     *
     * @param event        要响应的事件
     * @param postingState 事件发送过程所需数据的封装
     * @param eventClass   响应的事件类型(也可能是event的父类类型)
     * @return 如果eventClass事件没有订阅者, 返回false
     */
    private boolean postSingleEventForEventType(Object event, PostingThreadState postingState,
                                                Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            //所有订阅了eventClass的事件集合
            subscriptions = subscriptionsByEventType.get(eventClass);
        }
        if (subscriptions != null && !subscriptions.isEmpty()) {
            //回调subscription的响应方法
            for (Subscription subscription : subscriptions) {
                postingState.event = event;
                postingState.subscription = subscription;
                boolean aborted = false;
                try {
                    postToSubscription(subscription, event, postingState.isMainThread);
                    aborted = postingState.canceled;
                } finally {
                    postingState.event = null;
                    postingState.subscription = null;
                    postingState.canceled = false;
                }
                if (aborted) {
                    break;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 回调订阅者的响应方法
     *
     * @param subscription 订阅者对象的封装
     * @param event        要响应的事件
     * @param isMainThread 是否在UI线程中
     */
    private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
        switch (subscription.subscriberMethod.threadMode) {
            case PostThread:
                //直接调用响应方法
                invokeSubscriber(subscription, event);
                break;
            case MainThread:
                //如果是主线程则直接调用响应事件,否则使用handle去在主线程响应事件
                if (isMainThread) {
                    invokeSubscriber(subscription, event);
                } else {
                    mainThreadPoster.enqueue(subscription, event);
                }
                break;
            case BackgroundThread:
                //如果要求是在后台线程回调,后台线程使用相应的线程
                if (isMainThread) {
                    backgroundPoster.enqueue(subscription, event);
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            case Async:
                asyncPoster.enqueue(subscription, event);
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + subscription
                        .subscriberMethod.threadMode);
        }
    }

    /**
     * 将参数eventClass的所有父类,父类的接口,父类接口的父类,全部添加到eventTypesCache集合中
     */
    private List<Class<?>> lookupAllEventTypes(Class<?> eventClass) {
        synchronized (eventTypesCache) {
            List<Class<?>> eventTypes = eventTypesCache.get(eventClass);
            if (eventTypes == null) {
                eventTypes = new ArrayList<Class<?>>();
                Class<?> clazz = eventClass;

                //通过循环,将父类,父类的接口,父类接口的父类,全部添加到eventTypes集合中
                while (clazz != null) {
                    eventTypes.add(clazz);
                    addInterfaces(eventTypes, clazz.getInterfaces());
                    clazz = clazz.getSuperclass();
                }

                eventTypesCache.put(eventClass, eventTypes);
            }
            return eventTypes;
        }
    }

    /**
     * 通过递归,将所有interfaces以及其父类接口添加到eventTypes中
     *
     * @param eventTypes 容纳接口集合
     * @param interfaces 要遍历的接口
     */
    static void addInterfaces(List<Class<?>> eventTypes, Class<?>[] interfaces) {
        for (Class<?> interfaceClass : interfaces) {
            // 只要当前接口没有被添加,就添加到集合中,并再次查找当前接口的父类接口
            if (!eventTypes.contains(interfaceClass)) {
                eventTypes.add(interfaceClass);
                addInterfaces(eventTypes, interfaceClass.getInterfaces());
            }
        }
    }

    /**
     * Invokes the subscriber if the subscriptions is still active. Skipping subscriptions
     * prevents race conditions between {@link #unregister(Object)} and event delivery.
     * Otherwise the event might be delivered after the subscriber unregistered.
     * This is particularly important for main thread delivery and
     * registrations bound to the live cycle of an Activity or Fragment.
     */
    void invokeSubscriber(PendingPost pendingPost) {
        Object event = pendingPost.event;
        Subscription subscription = pendingPost.subscription;
        PendingPost.releasePendingPost(pendingPost);
        if (subscription.active) {
            invokeSubscriber(subscription, event);
        }
    }

    /**
     * 调用订阅者中的回调方法去响应事件
     *
     * @param subscription 订阅者封装类的对象
     * @param event        要响应的事件
     */
    void invokeSubscriber(Subscription subscription, Object event) {
        try {
            //调用subscription.subscriber对象中的subscription.subscriberMethod.method方法,并传递参数event
            subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
        } catch (InvocationTargetException e) {
            handleSubscriberException(subscription, event, e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    private void handleSubscriberException(Subscription subscription, Object event, Throwable
            cause) {
        if (event instanceof SubscriberExceptionEvent) {
            if (logSubscriberExceptions) {
                // Don't send another SubscriberExceptionEvent to avoid infinite event recursion,
                // just log
                Log.e(TAG, "SubscriberExceptionEvent subscriber " + subscription.subscriber
                        .getClass()
                        + " threw an exception", cause);
                SubscriberExceptionEvent exEvent = (SubscriberExceptionEvent) event;
                Log.e(TAG, "Initial event " + exEvent.causingEvent + " caused exception in "
                        + exEvent.causingSubscriber, exEvent.throwable);
            }
        } else {
            if (throwSubscriberException) {
                throw new EventBusException("Invoking subscriber failed", cause);
            }
            if (logSubscriberExceptions) {
                Log.e(TAG, "Could not dispatch event: " + event.getClass() + " to subscribing " +
                        "class "
                        + subscription.subscriber.getClass(), cause);
            }
            if (sendSubscriberExceptionEvent) {
                SubscriberExceptionEvent exEvent = new SubscriberExceptionEvent(this, cause, event,
                        subscription.subscriber);
                post(exEvent);
            }
        }
    }

    /**
     * 事件发送过程所需数据的封装
     */
    final static class PostingThreadState {
        final List<Object> eventQueue = new ArrayList<Object>();
        boolean isPosting;
        boolean isMainThread;
        Subscription subscription;
        Object event;
        boolean canceled;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    // Just an idea: we could provide a callback to post() to be notified, an alternative would 
    // be events, of course...
    /* public */interface PostCallback {
        void onPostCompleted(List<SubscriberExceptionEvent> exceptionEvents);
    }

    //////////////////////////////////////   Chidori   ////////////////////////////////////////////

    private Class<?>[] serverClazz;

    public void connect(Class<?>... clazz) {
        serverClazz = clazz;
    }

    public void postRemote(String remotePkg, Parcelable event) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(ChidoriClient.CHIDORI_EVENT, event);
        send(remotePkg, bundle);
    }

    public void postRemote(String remotePkg, int event) {
        Bundle bundle = new Bundle();
        bundle.putInt(ChidoriClient.CHIDORI_EVENT, event);
        send(remotePkg, bundle);
    }

    public void postRemote(String remotePkg, double event) {
        Bundle bundle = new Bundle();
        bundle.putDouble(ChidoriClient.CHIDORI_EVENT, event);
        send(remotePkg, bundle);
    }

    public void postRemote(String remotePkg, long event) {
        Bundle bundle = new Bundle();
        bundle.putLong(ChidoriClient.CHIDORI_EVENT, event);
        send(remotePkg, bundle);
    }

    public void postRemote(String remotePkg, char event) {
        Bundle bundle = new Bundle();
        bundle.putChar(ChidoriClient.CHIDORI_EVENT, event);
        send(remotePkg, bundle);
    }

    public void postRemote(String remotePkg, String event) {
        Bundle bundle = new Bundle();
        bundle.putString(ChidoriClient.CHIDORI_EVENT, event);
        send(remotePkg, bundle);
    }

    private void send(String remotePkg, Bundle bundle) {
        Intent sendEventIntent = new Intent(ChidoriClient.CHIDORI_ACTION);
        sendEventIntent.putExtra(ChidoriClient.CHIDORI_FILTER, remotePkg);
        sendEventIntent.putExtra(ChidoriClient.CHIDORI_SERVER, serverClazz);
        sendEventIntent.putExtra(ChidoriClient.CHIDORI_WRAPPER_DATA, bundle);
        ApplicationHolder.getAppContext().sendBroadcast(sendEventIntent);
        serverClazz = null;
    }
}
