/*
 * #%L
 * wcm.io
 * %%
 * Copyright (C) 2024 wcm.io
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package io.wcm.handler.mediasource.dam;

import static com.day.cq.dam.api.DamConstants.PREFIX_ASSET_THUMBNAIL;
import static com.day.cq.dam.api.DamConstants.PREFIX_ASSET_WEB;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.day.cq.dam.api.Rendition;

/**
 * Defines the different types of renditions generated automatically by AEM.
 */
public enum AemRenditionType {

  /**
   * Thumbnail rendition (with <code>cq5dam.thumbnail.</code> prefix).
   */
  THUMBNAIL_RENDITION(Pattern.compile("^" + Pattern.quote(PREFIX_ASSET_THUMBNAIL) + "\\..*$")),

  /**
   * Web rendition for the image editor/cropping (with <code>cq5dam.web.</code> prefix).
   */
  WEB_RENDITION(Pattern.compile("^" + Pattern.quote(PREFIX_ASSET_WEB) + "\\..*$")),

  /**
   * Video rendition (with <code>cq5dam.video.</code> prefix).
   */
  VIDEO_RENDITION(Pattern.compile("^cq5dam\\.video\\..*$")),

  /**
   * Any other rendition generated by AEM (with <code>cq5dam.</code> or <code>cqdam.</code> prefix).
   */
  OTHER_RENDITION(Pattern.compile("^(cq5dam|cqdam)\\..*$"));

  private final Pattern namePattern;

  AemRenditionType(@NotNull Pattern namePattern) {
    this.namePattern = namePattern;
  }

  /**
   * @param renditionName Rendition name
   * @return true if Rendition name matches with this type
   */
  public boolean matches(@NotNull String renditionName) {
    return namePattern.matcher(renditionName).matches();
  }

  /**
   * @param rendition Rendition
   * @return true if Rendition name matches with this type
   */
  public boolean matches(@NotNull Rendition rendition) {
    return matches(rendition.getName());
  }

  /**
   * @param renditionName Rendition name
   * @return Matching AEM rendition type or null if no match
   */
  @SuppressWarnings("null")
  public static @Nullable AemRenditionType forRendition(@NotNull String renditionName) {
    return Stream.of(AemRenditionType.values())
        .filter(type -> type.matches(renditionName))
        .findFirst()
        .orElse(null);
  }

  /**
   * @param rendition Rendition
   * @return Matching AEM rendition type or null if no match
   */
  public static @Nullable AemRenditionType forRendition(@NotNull Rendition rendition) {
    return forRendition(rendition.getName());
  }

}
