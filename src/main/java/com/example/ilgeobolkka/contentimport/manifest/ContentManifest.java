package com.example.ilgeobolkka.contentimport.manifest;

public sealed interface ContentManifest
        permits InitialContentManifest, AiRouteContentManifest {

    String contentVersion();
}
