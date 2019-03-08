# light-router

A client-side service mesh router designed for the legacy system that cannot leverage the light-4j client module

[Stack Overflow](https://stackoverflow.com/questions/tagged/light-4j) |
[Google Group](https://groups.google.com/forum/#!forum/light-4j) |
[Gitter Chat](https://gitter.im/networknt/light-router) |
[Subreddit](https://www.reddit.com/r/lightapi/) |
[Youtube Channel](https://www.youtube.com/channel/UCHCRMWJVXw8iB7zKxF55Byw) |
[Documentation](https://doc.networknt.com/service/router/) |
[Contribution Guide](https://doc.networknt.com/contribute/) |

### Framework usage workflow diagram

![workflow](docs/light-route.png)

### Light-router provides the Router Assisted Service Discovery


Light-router is a service that provides consumers with another option to make service discovery if they cannot leverage the client module provided by light-4j.

Light-router is primarily used for service discovery and technically there is client-side discovery only as it is called “service discovery” and only clients need to do that. All discovery can just exist on client-side.

The difference is that the discovery code in client or on the client host or on another static server in a data center. An additional scenario is to use light-router as BFF for SPA or Mobile.


### To learn how to use light-router, pleases refer to

* [Getting Started](https://www.networknt.com/getting-started/light-router/) to learn core concepts
* [Tutorial](https://www.networknt.com/tutorial/router/) with step by step guide for RESTful proxy
* [Configuration](https://www.networknt.com/service/router/configuration/) for different configurations based on your situations
