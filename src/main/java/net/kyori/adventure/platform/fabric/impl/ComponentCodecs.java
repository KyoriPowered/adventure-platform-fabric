/*
 * This file is part of adventure-platform-fabric, licensed under the MIT License.
 *
 * Copyright (c) 2024 KyoriPowered
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.kyori.adventure.platform.fabric.impl;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.platform.fabric.impl.server.FriendlyByteBufBridge;
import net.kyori.adventure.pointer.Pointered;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import org.jetbrains.annotations.Nullable;

/**
 * Common codec storage place.
 *
 * <p>These should not be referenced directly.</p>
 */
public final class ComponentCodecs {

  private ComponentCodecs() {
  }

  public static Codec<Component> unwrappingToNative(final Codec<Component> original) {
    return new Codec<Component>() {
      @Override
      @SuppressWarnings("unchecked")
      public <T> DataResult<T> encode(final Component input, final DynamicOps<T> ops, final T prefix) {
        if (input instanceof WrappedComponent w) {
          if (w.deepConvertedIfPresent() != null) { // already converted
            return original.encode(w.deepConvertedIfPresent(), ops, prefix);
          }

          final JsonElement json = GsonComponentSerializer.gson().serializeToTree(w.wrapped());
          if (ops instanceof JsonOps) {
            return DataResult.success((T) json);
          } else if (ops instanceof RegistryOps<?> && ops instanceof DelegatingOpsBridge deleg && deleg.adventure$bridge$delegate() instanceof JsonOps) {
            return DataResult.success((T) json);
          } else {
            return DataResult.success(JsonOps.INSTANCE.convertTo(ops, json));
          }
        }

        return original.encode(input, ops, prefix);
      }

      @Override
      public <T> DataResult<Pair<Component, T>> decode(final DynamicOps<T> ops, final T input) {
        return original.decode(ops, input);
      }
    }.withLifecycle(Lifecycle.stable());
  }

  public static <T extends ByteBuf> StreamCodec<T, Component> translatingStreamCodec(final StreamCodec<T, Component> original) {
    return new StreamCodec<>() {
      @Override
      public Component decode(final T buf) {
        return original.decode(buf);
      }

      @Override
      public void encode(final T buf, final Component component) {
        if (buf instanceof FriendlyByteBufBridge) {
          final @Nullable Pointered adventure$data = ((FriendlyByteBufBridge) buf).adventure$data();
          if (adventure$data != null && component instanceof WrappedComponent input) {
            original.encode(buf, input.rendered(adventure$data));
            return;
          }
        }

        original.encode(buf, component);
      }
    };
  }
}
