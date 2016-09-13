(ns cosycat.settings.components.corpora
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [dropdown-select throbbing-panel]]
            [cosycat.settings.components.shared-components :refer [row-component]]
            [cosycat.query-backends.core :refer [ensure-corpus]]
            [cosycat.tree :refer [data-tree]]
            [taoensso.timbre :as timbre]))

(defn corpus-info-component [{:keys [name info] :as corpus-config}]
  (ensure-corpus corpus-config)
  (fn [{:keys [name info] :as corpus-config}]
    [:div.container
     [:div.row [bs/label name]]
     [:div.row [:br]]
     (if (nil? info) [:div.row [throbbing-panel]] [:div.row [data-tree info :init-open false]])
     [:div.row [:hr]]]))

(defn corpus-info []
  (let [corpora (re-frame/subscribe [:corpora])]
    (fn []
     [:div.container-fluid
      (doall (for [{:keys [name] :as corpus-config} @corpora]
               ^{:key name} [corpus-info-component corpus-config]))])))

{:description "",
 :displayName "brown-tei",
 :contentViewable false,
 :documentFormat "nl.inl.blacklab.indexers.DocIndexerBrownTei",
 :complexFields
 {:contents {:displayName "Contents",
             :description "",
             :mainProperty "word",
             :basicProperties {:id {:sensitivity "ONLY_INSENSITIVE"},
                               :pos {:sensitivity "ONLY_INSENSITIVE"},
                               :subpos {:sensitivity "ONLY_INSENSITIVE"},
                               :word {:sensitivity "SENSITIVE_AND_INSENSITIVE"}}}},
 :tokenCount 1155866,
 :metadataFields
 {:bibl {:fieldName "bibl",
         :displayName "Bibl",
         :type "TEXT",
         :group ""},
  :fromInputFile {:fieldName "fromInputFile",
                  :displayName "From input file",
                  :type "TEXT",
                  :group nil},
  :idno {:fieldName "idno",
         :displayName "Idno",
         :type "TEXT",
         :group ""},
  :title {:fieldName "title",
          :displayName "Title",
          :type "TEXT",
          :group ""}},
 :status "available",
 :fieldInfo {:pidField "",
             :titleField "title",
             :authorField "",
             :dateField ""},
 :versionInfo {:blackLabBuildTime "2016-06-30 15:46:03",
               :indexFormat "3.1",
               :timeCreated "2016-06-30 15:47:14",
               :timeModified "2016-06-30 15:47:14"},
 :indexName "brown-tei"}
