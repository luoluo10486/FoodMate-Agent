package com.foodmate.bootstrap;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables Runtime outbox and reconciliation schedules. */
@Configuration
@EnableScheduling
public class RuntimeSchedulingConfiguration {}
