package com.fund.trade.domain.event;

/**
 * 领域事件发布端口（领域层定义，基础设施层实现适配）。
 */
public interface DomainEventPublisher {

    /** 发布领域事件（实现方保证在同一事务内同步分发） */
    void publish(DomainEvent event);
}
