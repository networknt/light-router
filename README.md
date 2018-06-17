# light-router


A client side service mesh router designed for legacy system that cannot leverage client module


[Developer Chat](https://gitter.im/networknt/light-4j) |
[Documentation](https://www.networknt.com/tutorial/common/discovery/router/) |
[Contribution Guide](CONTRIBUTING.md) |


## Framework usage workflow diagram


![workflow](docs/light-route.png)


## Light-router provides the router Assisted Service Discovery


Light-router is a service that provides consumers another option to do service discovery if they cannot leverage client module provided by light-4j.


Light-router is primarily used for service discovery and technically there is client-side discovery only as it is called “service discovery” and only clients need to do that. All discovery can only exist on client-side.



The difference is that the discovery code in client or on the client host or on another static server in a data center. An additional scenario is to use light-router as BFF for SPA or Mobile.



### To learn how to use light-router, pleases refer to

* [Getting Started](https://www.networknt.com/getting-started/light-router/) to learn core concepts
* [Tutorial](https://www.networknt.com/tutorial/router/) with step by step guide for RESTful proxy
* [Configuration](https://www.networknt.com/service/router/configuration/) for different configurations based on your situations
