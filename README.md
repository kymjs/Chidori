[![OSL](http://cdn.kymjs.com/image/logo_s.png)](http://www.kymjs.com/works/)
=================

## 跨进程 EventBus

为 EventBus 添加跨进程通信设计。

## 使用只需三步

#### 1.定义事件类型

```
public class MessageEvent implements Parcelable { 
/* Additional fields if needed */ 
}
```

#### 2.定义事件响应者

事件类型的方法名必须是四者之一（onEvent、onEventMainThread、onEventBackgroundThread、onEventAsync）  
onEvent：在哪个线程发送事件的就在哪个线程响应  
onEventMainThread：无论哪个线程发送都在主线程响应  
onEventBackgroundThread：无论哪个线程发送都在子线程响应(串行)  
onEventAsync：无论哪个线程发送都在子线程响应(并行)  

```
//参数为事件类型
public void onEvent(MessageEvent event) {
/* Do something */
}

//注册与解除注册事件响应者
 @Override
 public void onStart() {
     super.onStart();
     EventBus.getDefault().register(this);
 }

 @Override
 public void onStop() {
     super.onStop();
     EventBus.getDefault().unregister(this);
 }

```

#### 3.发送事件

```
//发送到当前进程
EventBus.getDefault().post(new MessageEvent());

//发送到当前应用的独立进程
//参数：事件接收者的包名，事件
EventBus.getDefault().postRemote("com.kymjs.demo",  new MessageEvent());

//跨应用发送事件
EventBus.getDefault().postRemote("com.kymjs.demo",  new MessageEvent());

```

## 注意  

1. 若接收者所在进程不是一个应用的主进程，需要声明一个 ```Service``` 继承 ```ChidoriServer``` 与接受者在同一个进程，并在执行```postRemote()```方法之前首先调用```connect(YourService.class)```做连接。  
2. 跨进程传递的事件必须实现```Parcelable```接口，这里推荐一个插件，自动生成 Parcelable 序列化的代码，叫：【Android Parcelable code generator】。

## 开源协议
```
 Copyright (C) 2017, 张涛
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 ```
