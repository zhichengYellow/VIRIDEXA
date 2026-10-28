package org.viridexa.api.science;
import java.util.*;
import org.springframework.boot.json.JsonParserFactory;

/** Explicit public projection: raw payloads, paths and internal review notes never leave this boundary. */
public final class PublicScientificData {
 private PublicScientificData(){}
 private static final Set<String> FIELDS=Set.of("scientific_name","scientificName","accepted_name","synonym_name","classification","trait","raw_value","rank","authorship","name_zh","source_namespace","source_taxon_id","source_name_id","parent_taxon_id","accepted_taxon_id","traits","normalized_traits","kind","unit","basis","analyte","value","study_id","sample_id");
 public static Map<String,Object> object(Object input){
  if(input instanceof Map<?,?> m){var out=new LinkedHashMap<String,Object>();m.forEach((k,v)->out.put(k.toString(),v));return out;}
  if(input instanceof String s){try{return JsonParserFactory.getJsonParser().parseMap(s);}catch(Exception e){return Map.of();}}
  return Map.of();
 }
 public static Map<String,Object> payload(Object input){
  var out=new LinkedHashMap<String,Object>();object(input).forEach((k,v)->{if(FIELDS.contains(k))out.put(k,safe(v));});return out;
 }
 private static Object safe(Object input){
  if(input instanceof String s && (s.contains("/Users/")||s.contains("/home/")||s.startsWith("/app/")||s.matches("^[A-Za-z]:.*")))return null;
  if(input instanceof Map<?,?> m){var out=new LinkedHashMap<String,Object>();m.forEach((k,v)->out.put(k.toString(),safe(v)));return out;}
  if(input instanceof List<?> l)return l.stream().map(PublicScientificData::safe).toList();return input;
 }
 public static List<Map<String,Object>> rows(List<Map<String,Object>> rows){
  return rows.stream().map(row->{var out=new LinkedHashMap<>(row);for(String key:List.of("normalized_payload","payload"))if(out.containsKey(key))out.put(key,payload(out.get(key)));
   out.remove("raw_payload");out.remove("reviewer_id");out.remove("rationale");out.remove("path");out.remove("source_path");
   if(out.containsKey("locator")){var loc=object(out.get("locator"));var clean=new LinkedHashMap<String,Object>();
    for(String key:List.of("pdf_page","printed_page","table","row","record_ordinal","record_id","archive_member","zip_member","all_extracted_samples_index","field","component_index"))if(loc.containsKey(key))clean.put(key,safe(loc.get(key)));out.put("locator",clean);}
   return (Map<String,Object>)out;}).toList();
 }
}
