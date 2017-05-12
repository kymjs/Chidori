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

import java.util.ArrayList;
import java.util.List;

final class PendingPost {
    //单例池,复用对象
    private final static List<PendingPost> pendingPostPool = new ArrayList<PendingPost>();

    Object event; //事件类型
    Subscription subscription; //订阅者
    PendingPost next; //队列下一个待发送对象

    private PendingPost(Object event, Subscription subscription) {
        this.event = event;
        this.subscription = subscription;
    }

    /**
     * 首先检查复用池中是否有可用,如果有则返回复用,否则返回一个新的
     *
     * @param subscription 订阅者
     * @param event        订阅事件
     * @return 待发送对象
     */
    static PendingPost obtainPendingPost(Subscription subscription, Object event) {
        synchronized (pendingPostPool) {
            int size = pendingPostPool.size();
            if (size > 0) {
                PendingPost pendingPost = pendingPostPool.remove(size - 1);
                pendingPost.event = event;
                pendingPost.subscription = subscription;
                pendingPost.next = null;
                return pendingPost;
            }
        }
        return new PendingPost(event, subscription);
    }

    /**
     * 回收一个待发送对象,并加入复用池
     *
     * @param pendingPost 待回收的待发送对象
     */
    static void releasePendingPost(PendingPost pendingPost) {
        pendingPost.event = null;
        pendingPost.subscription = null;
        pendingPost.next = null;
        synchronized (pendingPostPool) {
            // 防止池无限增长
            if (pendingPostPool.size() < 1000) {
                pendingPostPool.add(pendingPost);
            }
        }
    }
}