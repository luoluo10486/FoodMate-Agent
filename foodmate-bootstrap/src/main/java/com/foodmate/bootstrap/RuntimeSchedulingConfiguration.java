package com.foodmate.bootstrap;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 启用 Runtime Outbox 和对账任务的定时调度。 */
@Configuration
@EnableScheduling
public class RuntimeSchedulingConfiguration {}
