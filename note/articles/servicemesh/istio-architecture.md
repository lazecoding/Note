# Istio 架构

- 目录
  - [设计目标](#设计目标)
  - [架构设计](#架构设计)
    - [Proxy](#Proxy)
      - [istio-proxy](#istio-proxy)
      - [polit-agent](#polit-agent)
    - [istiod](#istiod)
      - [Pilot](#Pilot)
      - [Citadel](#Citadel)
      - [Galley](#Galley)

Istio 是目前最流行的服务网格的开源实现，它的架构目前来说有两种，一种是 Sidecar 模式，这也是 Istio 最传统的部署架构，另一种是 Proxyless 模式。

本文阐述 Sidecar 模式。

### 设计目标

几个关键的设计目标形成了 Istio 的架构，这些目标对于使系统能够大规模和高性能地处理服务是至关重要的。

- 对应用透明性：从本质上说，对应用透明是 Service Mesh 的特性，一个合格的 Service Mesh 产品都应该具有这一特性，否则也就失去了网格产品的核心竞争力。为此，Istio 自动将自己注入到服务之间的所有网络路径中，做到对应用的透明性。Istio 使用 Sidecar 代理来捕获流量，并在不更改已部署应用程序代码的情况下，自动对网络层进行配置，以实现通过这些代理来路由流量。
- 可扩展性：Istio 认为，运维和开发人员随着深入使用 Istio 提供的功能，会逐渐涌现更多的需求，主要集中在策略方面。因此，为策略系统提供足够的扩展性，成为了 Istio 的一个主要的设计目标。
- 可移植性：考虑到现有云生态的多样性，Istio 被设计为可以支持不同的底层平台，也支持本地、虚拟机、云平台等不同的部署环境。不过从目前的情况来看，Istio 和 Kubernetes 还是有着较为紧密的依赖关系，平台无关性、可移植性将是 Istio 最终实现目标。
- 策略一致性：Istio 使用自己的 API 将策略系统独立出来，而不是集成到 Sidecar 中，从而允许服务根据需要直接与之集成。同时，Istio 在配置方面也注重统一和用户体验的一致性。一个典型的例子是路由规则都统一由虚拟服务来配置，可在网格内、外以及边界的流量控制中复用。

### 架构设计

Istio 服务网格从逻辑上分为数据平面和控制平面。

- 数据平面：由一组智能代理（Envoy）组成，被部署为 Sidecar。这些代理负责协调和控制微服务之间的所有网络通信。它们还收集和报告所有网格流量的遥测数据。
- 控制平面：管理并配置代理来进行流量路由。

下图展示了组成每个平面的不同组件：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/servicemesh/istio-service-mesh-architecture.png" width="600px">
</div>

#### Proxy

Proxy 位于数据平面，即：常说的 Sidecar 代理，与应用服务以 Sidecar 方式部署在同一个 Pod 中。Proxy 实际上包括 istio-proxy 和 pilot-agent 两部分，它们以两个不同的进程部署在同一个容器 istio-proxy 中。

##### istio-proxy

Istio 的数据平面默认使用 Envoy 的扩展版本作为 Sidecar 代理（即：istio-proxy），istio-proxy 是基于 Envoy 新增了一些扩展。

Envoy 是用 C++ 开发的高性能代理，用于协调服务网格中所有服务的入站和出站流量，是唯一与数据平面流量交互的组件。主要包括三部分能力：

- 动态服务发现、负载均衡、路由、流量转移。
- 弹性能力：如超时重试、熔断等。
- 调试功能：如故障注入、流量镜像等。

> 注：理论上，Istio 是支持多种 Sidecar 代理，其中 Envoy 作为默认提供的数据平面，如无特殊说明在 Istio 中通常所说的 Envoy 就是 istio-proxy。

##### polit-agent

pilot-agent，负责管理 istio-proxy 的整个生命周期，具体包括 istio-proxy 准备启动参数和配置文件，负责管理 istio-proxy 的启动过程、运行状态监控以及重启等。

部署上，isito-proxy 不是单独构建镜像，而是和 polit-agent 一起打包构建成一个镜像 istio/proxyv2，poilt-agent 将会以子进程的方式启动 istio-proxy，并监控 istio-proxy 的运行状态。

#### istiod

自 Istio 1.5 版本开始，控制平面由原来分散、独立部署的三个组件（Pilot、Citadel、Galley）整合为一个独立的 istiod，变成了一个单进程、多模块的组织形态，极大的降低了原来部署的复杂度。

##### Pilot

负责 Istio 数据平面的 xDS 配置管理，具体包括：

- 服务发现、配置规则发现：为 Sidecar 提供服务发现、用于智能路由的流量管理功能（例如，A/B 测试、金丝雀发布等）以及弹性功能（超时、重试、熔断器等）。通过提供通用的流量管理模型和服务发现适配器（Service Discovery Adapter），来对接不同平台的适配层。
- xDS 配置下发：提供统一的 xDS API，供 Sidecar 调用。将路由规则等配置信息转换为 Sidecar 可以识别的信息，并下发给数据平面。

> 注：这里实际上是指 pilot-discovery。

##### Citadel

负责安全证书的管理和发放，可以实现授权和认证等操作。

Citadel 并不是唯一的证书管理方式，Istio 当前支持 Citadel、Vault 和 Google 等多种证书管理方式，Citadel 是当前默认的证书管理方式。

##### Galley

Galley 是 Istio 1.1 版本中引入的配置管理组件，主要负责配置的验证、提取和处理等功能。其目的是将 Istio 和底层平台（如 Kubernetes）进行解耦。

在引入 Galley 之前，Istio 控制平面的各个组件需要分别对 Kubernetes 资源进行管理，包括资源的配置验证，监控资源配置变化，并针对配置变更采取相应的处理等。