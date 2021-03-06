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

package com.google.cloud.dataflow.sdk.runners.worker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.cloud.dataflow.sdk.TestUtils;
import com.google.cloud.dataflow.sdk.coders.Coder;
import com.google.cloud.dataflow.sdk.coders.StringUtf8Coder;
import com.google.cloud.dataflow.sdk.coders.TextualIntegerCoder;
import com.google.cloud.dataflow.sdk.util.CoderUtils;
import com.google.cloud.dataflow.sdk.util.ShardingWritableByteChannel;
import com.google.cloud.dataflow.sdk.util.WindowedValue;
import com.google.cloud.dataflow.sdk.util.common.worker.Sink;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Tests for TextSink.
 */
@RunWith(JUnit4.class)
public class TextSinkTest {
  @Rule
  public TemporaryFolder tmpFolder = new TemporaryFolder();

  <T> void runTestWriteFile(List<T> elems,
                            @Nullable String header,
                            @Nullable String footer,
                            Coder<T> coder) throws Exception {
    File tmpFile = tmpFolder.newFile("file.txt");
    TextSink<WindowedValue<T>> textSink = TextSink.createForTest(
        tmpFile.getPath(), true, header, footer, coder);
    List<String> expected = new ArrayList<>();
    List<Integer> actualSizes = new ArrayList<>();
    if (header != null) {
      expected.add(header);
    }
    try (Sink.SinkWriter<WindowedValue<T>> writer = textSink.writer()) {
      for (T elem : elems) {
        actualSizes.add((int) writer.add(WindowedValue.valueInGlobalWindow(elem)));
        byte[] encodedElem = CoderUtils.encodeToByteArray(coder, elem);
        String line = new String(encodedElem);
        expected.add(line);
      }
    }
    if (footer != null) {
      expected.add(footer);
    }

    List<String> actual = new ArrayList<>();
    List<Integer> expectedSizes = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(tmpFile))) {
      for (;;) {
        String line = reader.readLine();
        if (line == null) {
          break;
        }
        actual.add(line);
        expectedSizes.add(line.length() + TextSink.NEWLINE.length);
      }
    }
    if (header != null) {
      expectedSizes.remove(0);
    }
    if (footer != null) {
      expectedSizes.remove(expectedSizes.size() - 1);
    }

    Assert.assertEquals(expected, actual);
    Assert.assertEquals(expectedSizes, actualSizes);
  }

  @Test
  public void testWriteEmptyFile() throws Exception {
    runTestWriteFile(Collections.<String>emptyList(), null, null,
                     StringUtf8Coder.of());
  }

  @Test
  public void testWriteEmptyFileWithHeaderAndFooter() throws Exception {
    runTestWriteFile(Collections.<String>emptyList(), "the head", "the foot",
                     StringUtf8Coder.of());
  }

  @Test
  public void testWriteNonEmptyFile() throws Exception {
    List<String> lines = Arrays.asList(
        "",
        "  hi there  ",
        "bob",
        "",
        "  ",
        "--zowie!--",
        "");
    runTestWriteFile(lines, null, null, StringUtf8Coder.of());
  }

  @Test
  public void testWriteNonEmptyFileWithHeaderAndFooter() throws Exception {
    List<String> lines = Arrays.asList(
        "",
        "  hi there  ",
        "bob",
        "",
        "  ",
        "--zowie!--",
        "");
    runTestWriteFile(lines, "the head", "the foot", StringUtf8Coder.of());
  }

  @Test
  public void testWriteNonEmptyNonStringFile() throws Exception {
    runTestWriteFile(TestUtils.INTS, null, null, TextualIntegerCoder.of());
  }


  private static class ThrowingWritableByteChannel extends ShardingWritableByteChannel {
    IOException exception = null;
    boolean open = true;
    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void close() throws IOException {
      open = false;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
      if (exception != null) {
        throw exception;
      }
      return src.remaining();
    }

    @Override
    public int writeToShard(int shardNum, ByteBuffer src) throws IOException {
      if (exception != null) {
        throw exception;
      }
      return src.remaining();
    }
  }

  @Test
  public void testWriteFileWithFooterThatThrowsException() throws Exception {
    ThrowingWritableByteChannel channel = new ThrowingWritableByteChannel();
    TextSink<WindowedValue<String>> sink =
        TextSink.createForTest("test-location", false, null, "test-footer", StringUtf8Coder.of());
    TextSink<WindowedValue<String>>.TextFileWriter writer = sink.new TextFileWriter(channel);
    channel.exception = new IOException("Test throwing exception during close");
    try {
      writer.close();
      fail();
    } catch (IOException e) {
      assertSame(e, channel.exception);
      assertFalse(channel.isOpen());
    }
  }

  @Test
  public void testWriteShardedFileWithFooterThatThrowsException() throws Exception {
    ThrowingWritableByteChannel channel = new ThrowingWritableByteChannel();
    TextSink<WindowedValue<String>> sink =
        TextSink.createForTest("test-location", false, null, "test-footer", StringUtf8Coder.of());
    TextSink<WindowedValue<String>>.ShardingTextFileWriter writer =
        sink.new ShardingTextFileWriter(channel);
    channel.exception = new IOException("Test throwing exception during close");
    try {
      writer.close();
      fail();
    } catch (IOException e) {
      assertSame(e, channel.exception);
      assertFalse(channel.isOpen());
    }
  }

  // TODO: sharded filenames
  // TODO: not appending newlines
}
