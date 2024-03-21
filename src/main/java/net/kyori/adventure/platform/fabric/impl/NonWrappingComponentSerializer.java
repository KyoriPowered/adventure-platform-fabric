/*
 * This file is part of adventure-platform-fabric, licensed under the MIT License.
 *
 * Copyright (c) 2020-2024 KyoriPowered
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

import com.google.common.base.Suppliers;
import com.mojang.serialization.JsonOps;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;

public final class NonWrappingComponentSerializer implements ComponentSerializer<Component, Component, net.minecraft.network.chat.Component> {
  public static final NonWrappingComponentSerializer INSTANCE = new NonWrappingComponentSerializer();

  private static final ThreadLocal<Boolean> BYPASS_IS_ALLOWED_FROM_SERVER = ThreadLocal.withInitial(() -> false);
  private final Supplier<HolderLookup.@NotNull Provider> holderProvider;

  private NonWrappingComponentSerializer() {
    this(Suppliers.ofInstance(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)));
  }

  private NonWrappingComponentSerializer(final @NotNull Supplier<HolderLookup.@NotNull Provider> provider) {
    this.holderProvider = provider;
  }

  public static boolean bypassIsAllowedFromServer() {
    return BYPASS_IS_ALLOWED_FROM_SERVER.get();
  }

  @Override
  public Component deserialize(final net.minecraft.network.chat.Component input) {
    if (input instanceof WrappedComponent) {
      return ((WrappedComponent) input).wrapped();
    }

    return GsonComponentSerializer.gson().deserializeFromTree(
      ComponentSerialization.CODEC.encodeStart(this.holderProvider.get().createSerializationContext(JsonOps.INSTANCE), input)
      .getOrThrow(false, $ -> {})
    );
  }

  @Override
  public MutableComponent serialize(final Component component) {
    BYPASS_IS_ALLOWED_FROM_SERVER.set(true);
    final MutableComponent mutableComponent;
    try {
      mutableComponent = net.minecraft.network.chat.Component.Serializer.fromJson(GsonComponentSerializer.gson().serializeToTree(component), this.holderProvider.get());
    } finally {
      BYPASS_IS_ALLOWED_FROM_SERVER.set(false);
    }
    return mutableComponent;
  }
}
