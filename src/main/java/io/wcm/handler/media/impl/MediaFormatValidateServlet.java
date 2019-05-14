/*
 * #%L
 * wcm.io
 * %%
 * Copyright (C) 2019 wcm.io
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
package io.wcm.handler.media.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;

import com.day.cq.i18n.I18n;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;

import io.wcm.handler.media.Media;
import io.wcm.handler.media.MediaArgs.MediaFormatOption;
import io.wcm.handler.media.MediaHandler;
import io.wcm.handler.media.format.MediaFormat;
import io.wcm.sling.commons.adapter.AdaptTo;
import io.wcm.sling.commons.request.RequestParam;
import io.wcm.wcm.commons.contenttype.ContentType;
import io.wcm.wcm.commons.contenttype.FileExtension;

/**
 * Validates if the given asset references matches with the given
 * set of media formats. This is used for the File Upload Granite UI component.
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
    extensions = FileExtension.JSON,
    selectors = MediaFormatValidateServlet.SELECTOR,
    resourceTypes = "sling/servlet/default",
    methods = HttpConstants.METHOD_GET)
public final class MediaFormatValidateServlet extends SlingSafeMethodsServlet {
  private static final long serialVersionUID = 1L;

  static final String SELECTOR = "wcm-io-handler-media-mediaformat-validate";

  static final String RP_MEDIA_FORMATS = "mediaFormats";
  static final String RP_MEDIA_FORMATS_MANDATORY = "mediaFormatsMandatory";
  static final String RP_MEDIA_CROPAUTO = "mediaCropAuto";
  static final String RP_MEDIA_REF = "mediaRef";

  /**
   * Prefix for i18n keys to generated messages for media invalid reasons.
   */
  public static final String MEDIA_INVALID_REASON_I18N_PREFIX = "io.wcm.handler.media.invalidReason.";
  private static final String ASSET_INVALID_I18N_KEY = "io.wcm.handler.media.assetInvalid";

  @Override
  protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws ServletException, IOException {

    // read and validated request parameters
    String[] mediaFormats = StringUtils.split(RequestParam.get(request, RP_MEDIA_FORMATS), ",");
    String[] mediaFormatsMandatoryStrings = StringUtils.split(RequestParam.get(request, RP_MEDIA_FORMATS_MANDATORY), ",");
    Boolean[] mediaFormatsMandatory = mediaFormatsMandatoryStrings != null ? Arrays.stream(mediaFormatsMandatoryStrings)
        .map(BooleanUtils::toBoolean)
        .toArray(size -> new Boolean[size]) : null;
    boolean mediaCropAuto = RequestParam.getBoolean(request, RP_MEDIA_CROPAUTO);
    String mediaRef = RequestParam.get(request, RP_MEDIA_REF);

    if (mediaFormats == null || mediaFormats.length == 0
        || StringUtils.isEmpty(mediaRef)) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    MediaFormatOption[] mediaFormatOptions = new MediaFormatOption[mediaFormats.length];
    for (int i = 0; i < mediaFormats.length; i++) {
      boolean mandatory = false;
      if (mediaFormatsMandatory != null) {
        if (mediaFormatsMandatory.length == 1) {
          // backward compatibility: support a single flag for all media formats
          mandatory = mediaFormatsMandatory[0];
        }
        else if (mediaFormatsMandatory.length > i) {
          mandatory = mediaFormatsMandatory[i];
        }
      }
      mediaFormatOptions[i] = new MediaFormatOption(mediaFormats[i], mandatory);
    }

    // try to resolve media
    MediaHandler mediaHandler = AdaptTo.notNull(request, MediaHandler.class);
    Media media = mediaHandler.get(mediaRef)
        .mediaFormatOptions(mediaFormatOptions)
        .autoCrop(mediaCropAuto)
        .build();

    // response
    I18n i18n = getI18n(request);
    try {
      JSONObject result = new JSONObject();
      result.put("valid", media.isValid());

      // reason strings when media resolution was not successful
      if (!media.isValid()) {
        result.put("reason", getI18nText(i18n,
            MEDIA_INVALID_REASON_I18N_PREFIX + media.getMediaInvalidReason().name()));
        result.put("reasonTitle", getI18nText(i18n, ASSET_INVALID_I18N_KEY));
      }

      // info block with additional resolution information
      JSONObject info = getInfoMessages(media, i18n);
      if (info != null) {
        result.put("info", info);
      }

      response.setContentType(ContentType.JSON);
      response.getWriter().write(result.toString());
    }
    catch (JSONException ex) {
      throw new ServletException(ex);
    }
  }

  private String getI18nText(I18n i18n, String key, Object... args) {
    try {
      return i18n.get(key, (String)null, args);
    }
    catch (MissingResourceException ex) {
      return key;
    }
  }

  private I18n getI18n(SlingHttpServletRequest request) {
    PageManager pageManager = AdaptTo.notNull(request.getResourceResolver(), PageManager.class);
    Page currentPage = pageManager.getContainingPage(request.getResource());
    if (currentPage != null) {
      Locale locale = currentPage.getLanguage(false);
      ResourceBundle resourceBundle = request.getResourceBundle(locale);
      return new I18n(resourceBundle);
    }
    return new I18n(request);
  }

  private JSONObject getInfoMessages(Media media, I18n i18n) throws JSONException {

    // check for optional media formats that could not be resolved (only relevant if there is more than 1 media format)
    List<MediaFormatOption> allMediaFormatOptions = Arrays.asList(media.getMediaRequest().getMediaArgs().getMediaFormatOptions());
    if (allMediaFormatOptions.size() > 1) {
      List<MediaFormat> unresolvedMediaFormats = allMediaFormatOptions.stream()
          .filter(option -> !option.isMandatory())
          .map(MediaFormatOption::getMediaFormat)
          .filter(mediaFormat -> !hasRenditionWithMediaFormat(media, mediaFormat))
          .collect(Collectors.toList());
      if (!unresolvedMediaFormats.isEmpty()) {
        JSONObject info = new JSONObject();
        info.put("message", getI18nText(i18n, "io.wcm.handler.media.assetInfounresolvedMediaFormats"));
        info.put("title", getI18nText(i18n, "io.wcm.handler.media.assetValid"));
        info.put("unresolvedMediaFormats", new JSONArray(unresolvedMediaFormats.stream()
            .map(mediaFormat -> mediaFormat.toString())
            .collect(Collectors.toList())));
        return info;
      }
    }

    return null;
  }

  private boolean hasRenditionWithMediaFormat(Media media, MediaFormat mediaFormat) {
    return media.getRenditions().stream()
        .filter(rendition -> mediaFormat.equals(rendition.getMediaFormat()))
        .findFirst().isPresent();
  }

}
