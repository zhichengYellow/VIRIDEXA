export interface PlantRecord {
  id: number
  nameZh: string
  scientificName: string | null
  nameEn: string | null
  familyNameZh: string | null
  genusNameLatin: string | null
  morphology: string | null
  distribution: string | null
  recordStatus: string
  scientificNameStatus: string
  sourceDocument: string | null
  sourcePrintedPages: string | null
  sourcePages: string | null
  sourcePdfSha256: string | null
}

export interface PlantSearchResult {
  query: string | null
  total: number
  items: PlantRecord[]
}
