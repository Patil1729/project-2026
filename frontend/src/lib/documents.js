export function latestDocuments(documents = []) {
  const latest = new Map();
  for (const document of documents) {
    const previous = latest.get(document.documentType);
    if (!previous || Number(document.documentVersion) > Number(previous.documentVersion)) latest.set(document.documentType, document);
  }
  return [...latest.values()];
}
export function hasRecommendationUpload(documents = []) {
  return latestDocuments(documents).some(d => d.documentType === 'RECOMMENDATION_LETTER' && ['UPLOADED', 'VERIFIED'].includes(d.status));
}
