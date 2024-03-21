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
package net.kyori.adventure.platform.fabric.impl.client;

import java.net.MalformedURLException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.chat.ChatType;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.platform.fabric.FabricAudiences;
import net.kyori.adventure.platform.fabric.impl.AdventureCommon;
import net.kyori.adventure.platform.fabric.impl.ControlledAudience;
import net.kyori.adventure.platform.fabric.impl.FabricAudiencesInternal;
import net.kyori.adventure.platform.fabric.impl.GameEnums;
import net.kyori.adventure.platform.fabric.impl.PointerProviderBridge;
import net.kyori.adventure.platform.fabric.impl.accessor.minecraft.world.level.LevelAccess;
import net.kyori.adventure.platform.fabric.impl.client.mixin.minecraft.resources.sounds.AbstractSoundInstanceAccess;
import net.kyori.adventure.pointer.Pointered;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClientAudience implements ControlledAudience {
  private final Minecraft client;
  private final FabricClientAudiencesImpl controller;

  public ClientAudience(final Minecraft client, final FabricClientAudiencesImpl renderer) {
    this.client = client;
    this.controller = renderer;
  }

  @Override
  public @NotNull FabricAudiencesInternal controller() {
    return this.controller;
  }

  @Override
  public void sendMessage(final @NotNull Component message) {
    this.client.gui.getChat().addMessage(this.controller.toNative(message));
  }

  private net.minecraft.network.chat.ChatType.Bound toMc(final ChatType.Bound bound) {
    return AdventureCommon.chatTypeToNative(bound, this.controller);
  }

  @Override
  public void sendMessage(final @NotNull Component message, final ChatType.@NotNull Bound boundChatType) {
    final net.minecraft.network.chat.ChatType.Bound bound = this.toMc(boundChatType);
    this.client.gui.getChat().addMessage(bound.decorate(this.controller.toNative(message)), null, GuiMessageTag.chatNotSecure());
  }

  @Override
  public void sendMessage(final @NotNull SignedMessage signedMessage, final ChatType.@NotNull Bound boundChatType) {
    final net.minecraft.network.chat.ChatType.Bound bound = this.toMc(boundChatType);
    final Component message = Objects.requireNonNullElse(signedMessage.unsignedContent(), Component.text(signedMessage.message()));

    this.client.gui.getChat().addMessage(bound.decorate(this.controller.toNative(message)), (MessageSignature) signedMessage.signature(), this.tag(signedMessage));
  }

  private GuiMessageTag tag(final SignedMessage message) {
    if (message == null) {
      return null;
    } else if (message.isSystem()) {
      return GuiMessageTag.system();
    } else if (message.unsignedContent() != null && !message.unsignedContent().equals(Component.text(message.message()))) {
      return GuiMessageTag.chatModified(message.message());
    } else if (message.signature() == null) {
      return GuiMessageTag.chatNotSecure();
    }

    return null;
  }

  @Override
  public void deleteMessage(final SignedMessage.@NotNull Signature signature) {
    this.client.gui.getChat().deleteMessage((MessageSignature) signature);
  }

  @Override
  @Deprecated
  public void sendMessage(final Identity source, final @NotNull Component message, final @NotNull MessageType type) {
    if (this.client.isBlocked(source.uuid())) return;

    final ChatVisiblity visibility = this.client.options.chatVisibility().get();
    if (type == MessageType.CHAT) {
      // Add to chat queue (following delay and such)
      if (visibility == ChatVisiblity.FULL) {
        this.client.gui.getChat().addMessage(this.controller.toNative(message), null, null);
      }
    } else {
      // Add immediately as a system message
      if (visibility == ChatVisiblity.FULL || visibility == ChatVisiblity.SYSTEM) {
        this.client.gui.getChat().addMessage(this.controller.toNative(message));
      }
    }
  }

  @Override
  public void sendActionBar(final @NotNull Component message) {
    this.client.gui.setOverlayMessage(this.controller.toNative(message), false);
  }

  @Override
  public void showTitle(final @NotNull Title title) {
    final net.minecraft.network.chat.@Nullable Component titleText = title.title() == Component.empty() ? null : this.controller.toNative(title.title());
    final net.minecraft.network.chat.@Nullable Component subtitleText = title.subtitle() == Component.empty() ? null : this.controller.toNative(title.subtitle());
    final Title.@Nullable Times times = title.times();
    this.client.gui.setTitle(titleText);
    this.client.gui.setSubtitle(subtitleText);
    this.client.gui.setTimes(
      this.adventure$ticks(times == null ? null : times.fadeIn()),
      this.adventure$ticks(times == null ? null : times.stay()),
      this.adventure$ticks(times == null ? null : times.fadeOut())
    );
  }

  @Override
  public <T> void sendTitlePart(final @NotNull TitlePart<T> part, final @NotNull T value) {
    Objects.requireNonNull(value, "value");
    if (part == TitlePart.TITLE) {
      this.client.gui.setTitle(this.controller.toNative((Component) value));
    } else if (part == TitlePart.SUBTITLE) {
      this.client.gui.setSubtitle(this.controller.toNative((Component) value));
    } else if (part == TitlePart.TIMES) {
      final Title.Times times = (Title.Times) value;
      this.client.gui.setTimes(
        this.adventure$ticks(times.fadeIn()),
        this.adventure$ticks(times.stay()),
        this.adventure$ticks(times.fadeOut())
      );
    } else {
      throw new IllegalArgumentException("Unknown TitlePart '" + part + "'");
    }
  }

  private int adventure$ticks(final @Nullable Duration duration) {
    return duration == null || duration.getSeconds() == -1 ? -1 : (int) (duration.toMillis() / 50);
  }

  @Override
  public void clearTitle() {
    this.client.gui.setTitle(null);
    this.client.gui.setSubtitle(null);
  }

  @Override
  public void resetTitle() {
    this.client.gui.resetTitleTimes();
    this.clearTitle();
  }

  @Override
  public void showBossBar(final @NotNull BossBar bar) {
    BossHealthOverlayBridge.listener(this.client.gui.getBossOverlay(), this.controller).add(bar);
  }

  @Override
  public void hideBossBar(final @NotNull BossBar bar) {
    BossHealthOverlayBridge.listener(this.client.gui.getBossOverlay(), this.controller).remove(bar);
  }

  private long seed(final @NotNull Sound sound) {
    if (sound.seed().isPresent()) {
      return sound.seed().getAsLong();
    } else {
      final @Nullable LocalPlayer player = this.client.player;
      if (player != null) {
        return ((LevelAccess) player).accessor$threadSafeRandom().nextLong();
      } else {
        return 0l;
      }
    }
  }

  @Override
  public void playSound(final @NotNull Sound sound) {
    final @Nullable LocalPlayer player = this.client.player;
    if (player != null) {
      this.playSound(sound, player.getX(), player.getY(), player.getZ());
    } else {
      // not in-game
      this.client.getSoundManager().play(new SimpleSoundInstance(FabricAudiences.toNative(sound.name()), GameEnums.SOUND_SOURCE.toMinecraft(sound.source()),
        sound.volume(), sound.pitch(), RandomSource.create(this.seed(sound)), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }
  }

  @Override
  public void playSound(final @NotNull Sound sound, final Sound.@NotNull Emitter emitter) {
    final Entity targetEntity;
    if (emitter == Sound.Emitter.self()) {
      targetEntity = this.client.player;
    } else if (emitter instanceof final Entity entity) {
      targetEntity = entity;
    } else {
      throw new IllegalArgumentException("Provided emitter '" + emitter + "' was not Sound.Emitter.self() or an Entity");
    }

    // Initialize with a placeholder event
    final EntityBoundSoundInstance mcSound = new EntityBoundSoundInstance(
      SoundEvents.ITEM_PICKUP,
      GameEnums.SOUND_SOURCE.toMinecraft(sound.source()),
      sound.volume(),
      sound.pitch(),
      targetEntity,
      this.seed(sound)
    );
    // Then apply the ResourceLocation of our real sound event
    ((AbstractSoundInstanceAccess) mcSound).setLocation(FabricAudiences.toNative(sound.name()));

    this.client.getSoundManager().play(mcSound);
  }

  @Override
  public void playSound(final @NotNull Sound sound, final double x, final double y, final double z) {
    this.client.getSoundManager().play(new SimpleSoundInstance(
      FabricAudiences.toNative(sound.name()),
      GameEnums.SOUND_SOURCE.toMinecraft(sound.source()),
      sound.volume(),
      sound.pitch(),
      RandomSource.create(this.seed(sound)),
      false,
      0,
      SoundInstance.Attenuation.LINEAR,
      x,
      y,
      z,
      false
    ));
  }

  @Override
  public void stopSound(final @NotNull SoundStop stop) {
    final @Nullable Key sound = stop.sound();
    final @Nullable ResourceLocation soundIdent = sound == null ? null : FabricAudiences.toNative(sound);
    final Sound.@Nullable Source source = stop.source();
    final @Nullable SoundSource category = source == null ? null : GameEnums.SOUND_SOURCE.toMinecraft(source);
    this.client.getSoundManager().stop(soundIdent, category);
  }

  @Override
  public void openBook(final @NotNull Book book) {
    this.client.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(book.pages().stream().map(this.controller::toNative).toList())));
  }

  @Override
  public void sendPlayerListHeader(final @NotNull Component header) {
    this.client.gui.getTabList().setHeader(header == Component.empty() ? null : this.controller.toNative(header));
  }

  @Override
  public void sendPlayerListFooter(final @NotNull Component footer) {
    this.client.gui.getTabList().setHeader(footer == Component.empty() ? null : this.controller.toNative(footer));
  }

  @Override
  public void sendPlayerListHeaderAndFooter(final @NotNull Component header, final @NotNull Component footer) {
    this.sendPlayerListHeader(header);
    this.sendPlayerListFooter(footer);
  }

  @Override
  public void sendResourcePacks(final @NotNull ResourcePackRequest request) {
    if (request.replace()) {
      this.client.getDownloadedPackSource().popAll();
    }

    for (final ResourcePackInfo info : request.packs()) {
      ListeningPackFeedbackWrapper.registerCallback(info.id(), request.callback(), this);

      try {
        this.client.getDownloadedPackSource().pushPack(info.id(), info.uri().toURL(), info.hash());
      } catch (final MalformedURLException ex) {
        request.callback().packEventReceived(info.id(), ResourcePackStatus.INVALID_URL, this);
      }
      // TODO: required, prompting?
    }
  }

  @Override
  public void removeResourcePacks(final @NotNull UUID id, final @NotNull UUID @NotNull ... others) {
    this.client.getDownloadedPackSource().popPack(id);
    for (final UUID other : others) {
      this.client.getDownloadedPackSource().popPack(other);
    }
  }

  @Override
  public void clearResourcePacks() {
    this.client.getDownloadedPackSource().popAll();
  }

  @Override
  public @NotNull Pointers pointers() {
    final @Nullable LocalPlayer clientPlayer = this.client.player;
    if (clientPlayer != null) {
      return ((PointerProviderBridge) clientPlayer).adventure$pointers();
    } else {
      return ((Pointered) this.client).pointers();
    }
  }
}
