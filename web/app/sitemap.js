import { SITE_URL } from "@/lib/site";

const lastmod = new Date("2026-11-01");

export default function sitemap() {
  return [
    { url: `${SITE_URL}/`, lastmod, changeFrequency: "monthly", priority: 1 },
    { url: `${SITE_URL}/tai-nguyen`, lastmod, changeFrequency: "monthly", priority: 0.9 },
    { url: `${SITE_URL}/huong-dan`, lastmod, changeFrequency: "monthly", priority: 0.8 },
    { url: `${SITE_URL}/an-toan`, lastmod, changeFrequency: "monthly", priority: 0.85 },
  ];
}
