/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.dataflow.sdk.options;

import com.google.common.base.Preconditions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Options that are used to control logging configuration on the Dataflow worker.
 */
@Description("Options that are used to control logging configuration on the Dataflow worker.")
public interface DataflowWorkerLoggingOptions extends PipelineOptions {
  /**
   * The set of log levels that can be used on the Dataflow worker.
   */
  public enum Level {
    DEBUG, ERROR, INFO, TRACE, WARN
  }

  /**
   * This option controls the default log level of all loggers without a log level override.
   */
  @Description("Controls the default log level of all loggers without a log level override.")
  @Default.Enum("INFO")
  Level getDefaultWorkerLogLevel();
  void setDefaultWorkerLogLevel(Level level);

  /**
   * This option controls the log levels for specifically named loggers.
   * <p>
   * Later options with equivalent names override earlier options.
   * <p>
   * See {@link WorkerLogLevelOverride} for more information on how to configure logging
   * on a per {@link Class}, {@link Package}, or name basis. If used from the command line,
   * the expected format is {@code Name#Level}, further details on
   * {@link WorkerLogLevelOverride#create(String)}.
   */
  @Description("This option controls the log levels for specifically named loggers. "
      + "The expected format is Name#Level. The Dataflow worker uses java.util.logging, which "
      + "supports a logging hierarchy based off of names that are \".\" separated. "
      + "For example, by specifying the value \"a.b.c.Foo#DEBUG\", the logger for the class "
      + "\"a.b.c.Foo\" will be configured to output logs at the DEBUG level. Similarly, "
      + "by specifying the value \"a.b.c#WARN\", all loggers underneath the \"a.b.c\" package "
      + "will be configured to output logs at the WARN level. Note that multiple overrides can "
      + "be specified and that later values with equivalent names override earlier values. Also, "
      + "note that when multiple overrides are specified, the exact name followed by the closest "
      + "parent takes precedence.")
  WorkerLogLevelOverride[] getWorkerLogLevelOverrides();
  void setWorkerLogLevelOverrides(WorkerLogLevelOverride... workerLogLevelOverrides);

  /**
   * Defines a log level override for a specific class, package, or name.
   * <p>
   * {@link java.util.logging} is used on the Dataflow worker harness and supports
   * a logging hierarchy based off of names that are "." separated. It is a common
   * pattern to have the logger for a given class share the same name as the class itself.
   * Given the classes {@code a.b.c.Foo}, {@code a.b.c.Xyz}, and {@code a.b.Bar}, with
   * loggers named {@code "a.b.c.Foo"}, {@code "a.b.c.Xyz"}, and {@code "a.b.Bar"} respectively,
   * we can override the log levels:
   * <ul>
   *    <li>for {@code Foo} by specifying the name {@code "a.b.c.Foo"} or the {@link Class}
   *    representing {@code a.b.c.Foo}.
   *    <li>for {@code Foo}, {@code Xyz}, and {@code Bar} by specifying the name {@code "a.b"} or
   *    the {@link Package} representing {@code a.b}.
   *    <li>for {@code Foo} and {@code Bar} by specifying both of their names or classes.
   * </ul>
   * Note that by specifying multiple overrides, the exact name followed by the closest parent
   * takes precedence.
   */
  public static class WorkerLogLevelOverride {
    private static final String SEPARATOR = "#";

    /**
     * Overrides the default log level for the passed in class.
     * <p>
     * This is equivalent to calling {@link #forName(String, DataflowWorkerLoggingOptions.Level)}
     * and passing in the {@link Class#getName() class name}.
     */
    public static WorkerLogLevelOverride forClass(Class<?> klass, Level level) {
      Preconditions.checkNotNull(klass, "Expected class to be not null.");
      return forName(klass.getName(), level);
    }

    /**
     * Overrides the default log level for the passed in package.
     * <p>
     * This is equivalent to calling {@link #forName(String, DataflowWorkerLoggingOptions.Level)}
     * and passing in the {@link Package#getName() package name}.
     */
    public static WorkerLogLevelOverride forPackage(Package pkg, Level level) {
      Preconditions.checkNotNull(pkg, "Expected package to be not null.");
      return forName(pkg.getName(), level);
    }

    /**
     * Overrides the default log level for the passed in name.
     * <p>
     * Note that because of the hierarchical nature of logger names, this will
     * override the log level of all loggers that have the passed in name or
     * a parent logger that has the passed in name.
     */
    public static WorkerLogLevelOverride forName(String name, Level level) {
      Preconditions.checkNotNull(name, "Expected name to be not null.");
      Preconditions.checkNotNull(level,
          "Expected level to be one of %s.", Arrays.toString(Level.values()));
      return new WorkerLogLevelOverride(name, level);
    }

    /**
     * Expects a value of the form {@code Name#Level}. The {@code Name} generally
     * represents the fully qualified Java {@link Class#getName() class name},
     * or fully qualified Java {@link Package#getName() package name}, or custom
     * logger name. The {@code Level} represents the log level and must be one
     * of {@link Level}.
     */
    @JsonCreator
    public static WorkerLogLevelOverride create(String value) {
      Preconditions.checkNotNull(value, "Expected value to be not null.");
      Preconditions.checkArgument(value.contains(SEPARATOR),
          "Expected '#' separator but none found within '%s'.", value);
      String[] parts = value.split(SEPARATOR, 2);
      Level level;
      try {
        level = Level.valueOf(parts[1]);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(String.format(
            "Unsupported log level '%s' requested. Must be one of %s.",
            parts[1], Arrays.toString(Level.values())));
      }
      return forName(parts[0], level);
    }

    private final String name;
    private final Level level;
    private WorkerLogLevelOverride(String name, Level level) {
      this.name = name;
      this.level = level;
    }

    public String getName() {
      return name;
    }

    public Level getLevel() {
      return level;
    }

    @JsonValue
    @Override
    public String toString() {
      return name + SEPARATOR + level;
    }
  }
}
