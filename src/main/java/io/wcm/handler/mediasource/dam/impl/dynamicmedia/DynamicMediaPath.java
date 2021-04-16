/*
 * #%L
 * wcm.io
 * %%
 * Copyright (C) 2020 wcm.io
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
package io.wcm.handler.mediasource.dam.impl.dynamicmedia;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.wcm.handler.media.CropDimension;
import io.wcm.handler.media.Dimension;
import io.wcm.handler.media.format.Ratio;
import io.wcm.handler.mediasource.dam.impl.DamContext;

/**
 * Build part of dynamic media/scene7 URL to render renditions.
 */
public final class DynamicMediaPath {

  /**
   * Fixed path part for dynamic media image serving API for serving images.
   */
  private static final String IMAGE_SERVER_PATH = "/is/image/";

  /**
   * Fixed path part for dynamic media image serving API for serving static content.
   */
  private static final String CONTENT_SERVER_PATH = "/is/content/";

  /**
   * Suffix is appended to static content dynamic media URLs that should be served with
   * Content-Disposition: attachment header.
   * This is configured via a custom ruleset, see https://wcm.io/handler/media/dynamic-media.html
   */
  public static final String DOWNLOAD_SUFFIX = "?cdh=attachment";

  private DynamicMediaPath() {
    // static methods only
  }

  /**
   * Build media path for serving static content via dynamic media/scene7.
   * @param damContext DAM context objects
   * @param contentDispositionAttachment Whether to send content disposition: attachment header for downloads
   * @return Media path
   */
  public static @NotNull String buildContent(@NotNull DamContext damContext, boolean contentDispositionAttachment) {
    StringBuilder result = new StringBuilder();
    result.append(CONTENT_SERVER_PATH).append(encodeDynamicMediaObject(damContext));
    if (contentDispositionAttachment) {
      result.append(DOWNLOAD_SUFFIX);
    }
    return result.toString();
  }

  /**
   * Build media path for rendering image via dynamic media/scene7.
   * @param damContext DAM context objects
   * @return Media path
   */
  public static @NotNull String buildImage(@NotNull DamContext damContext) {
    return IMAGE_SERVER_PATH + encodeDynamicMediaObject(damContext);
  }

  /**
   * Build media path for rendering image with dynamic media/scene7.
   * @param damContext DAM context objects
   * @param width Width
   * @param height Height
   * @return Media path
   */
  public static @NotNull String buildImage(@NotNull DamContext damContext, long width, long height) {
    return buildImage(damContext, width, height, null, null);
  }

  /**
   * Build media path for rendering image with dynamic media/scene7.
   * @param damContext DAM context objects
   * @param width Width
   * @param height Height
   * @param cropDimension Crop dimension
   * @param rotation Rotation
   * @return Media path
   */
  public static @NotNull String buildImage(@NotNull DamContext damContext, long width, long height,
      @Nullable CropDimension cropDimension, @Nullable Integer rotation) {
    Dimension dimension = calcWidthHeight(damContext, width, height);

    if (cropDimension != null && cropDimension.isAutoCrop() && rotation == null) {
      // auto-crop applied - check for matching image profile and use predefined cropping preset if match found
      Optional<NamedDimension> smartCroppingDef = getSmartCropDimension(damContext, width, height);
      if (smartCroppingDef.isPresent()) {
        return IMAGE_SERVER_PATH + encodeDynamicMediaObject(damContext) + "%3A" + smartCroppingDef.get().getName();
      }
    }

    StringBuilder result = new StringBuilder();
    result.append(IMAGE_SERVER_PATH).append(encodeDynamicMediaObject(damContext)).append("?");
    if (cropDimension != null) {
      result.append("crop=").append(cropDimension.getCropStringWidthHeight()).append("&");
    }
    if (rotation != null) {
      result.append("rotate=").append(rotation).append("&");
    }
    result.append("wid=").append(dimension.getWidth()).append("&")
        .append("hei=").append(dimension.getHeight()).append("&")
        // cropping/width/height is pre-calculated to fit with original ratio, make sure there are no 1px background lines visible
        .append("fit=stretch");
    return result.toString();
  }

  /**
   * Checks if width or height is bigger than the allowed max. width/height.
   * Reduces both to the max limit keeping aspect ration is required.
   * @param width With
   * @param height Height
   * @return Dimension with capped width/height
   */
  private static Dimension calcWidthHeight(@NotNull DamContext damContext, long width, long height) {
    Dimension sizeLimit = damContext.getDynamicMediaImageSizeLimit();
    if (width > sizeLimit.getWidth()) {
      double ratio = Ratio.get(width, height);
      long newWidth = sizeLimit.getWidth();
      long newHeight = Math.round(newWidth / ratio);
      return calcWidthHeight(damContext, newWidth, newHeight);
    }
    if (height > sizeLimit.getHeight()) {
      double ratio = Ratio.get(width, height);
      long newHeight = sizeLimit.getHeight();
      long newWidth = Math.round(newHeight * ratio);
      return new Dimension(newWidth, newHeight);
    }
    return new Dimension(width, height);
  }

  private static Optional<NamedDimension> getSmartCropDimension(@NotNull DamContext damContext, long width, long height) {
    ImageProfile imageProfile = damContext.getImageProfile();
    if (imageProfile != null) {
      return imageProfile.getSmartCropDefinitions().stream()
          .filter(def -> (def.getWidth() == width) && (def.getHeight() == height))
          .findFirst();
    }
    return Optional.empty();
  }

  /**
   * Splits dynamic media folder and file name and URL-encodes them separately (may contain spaces or special chars).
   * @param damContext DAM context
   * @return Encoded path
   */
  private static String encodeDynamicMediaObject(@NotNull DamContext damContext) {
    String[] pathParts = StringUtils.split(damContext.getDynamicMediaObject(), "/");
    try {
      for (int i = 0; i < pathParts.length; i++) {
        pathParts[i] = URLEncoder.encode(pathParts[i], StandardCharsets.UTF_8.name());
      }
    }
    catch (UnsupportedEncodingException ex) {
      throw new RuntimeException("Unsupported encoding.", ex);
    }
    return StringUtils.join(pathParts, "/");
  }

}
