package com.example.ilgeobolkka.contentimport.manifest;

public sealed interface ContentManifest
        permits InitialContentManifest, AiRouteContentManifest {

    String INITIAL_CONTENT_VERSION = "initial-v1";
    String AI_ROUTE_CONTENT_VERSION = "ai-route-v2";

    String contentVersion();
}
