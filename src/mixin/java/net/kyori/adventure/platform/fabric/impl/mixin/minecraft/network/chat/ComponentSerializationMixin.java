/*
 * This file is part of adventure-platform-fabric, licensed under the MIT License.
 *
 * Copyright (c) 2023-2024 KyoriPowered
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
package net.kyori.adventure.platform.fabric.impl.mixin.minecraft.network.chat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.platform.fabric.impl.ComponentCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ComponentSerialization.class)
public abstract class ComponentSerializationMixin {
  // Inject our component logic into the codec
  // This is pretty inefficient for non-JsonOps codecs, but it is a minimal change which allows us to continue functioning.
  @Inject(method = "createCodec", at = @At("RETURN"), cancellable = true)
  private static void adventure$wrapCodec(final Codec<Component> recursive, final CallbackInfoReturnable<Codec<Component>> cir) {
    final Codec<Component> original = cir.getReturnValue();
    cir.setReturnValue(ComponentCodecs.unwrappingToNative(original));
  }

  // inject stream codecs for translation
  @WrapOperation(method = "<clinit>()V", at = {
    @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/ByteBufCodecs;fromCodecWithRegistries(Lcom/mojang/serialization/Codec;)Lnet/minecraft/network/codec/StreamCodec;"),
    @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/ByteBufCodecs;fromCodecWithRegistriesTrusted(Lcom/mojang/serialization/Codec;)Lnet/minecraft/network/codec/StreamCodec;"),
    @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/ByteBufCodecs;fromCodecTrusted(Lcom/mojang/serialization/Codec;)Lnet/minecraft/network/codec/StreamCodec;")
  })
  private static <T extends ByteBuf> StreamCodec<T, Component> adventure$wrapStreamCodec(final Codec<Component> codec, final Operation<StreamCodec<T, Component>> op) {
    return ComponentCodecs.translatingStreamCodec(op.call(codec));
  }
}
