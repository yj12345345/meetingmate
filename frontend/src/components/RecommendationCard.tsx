import type {PlaceRecommendation} from "../types/recommendation";

interface Props {
    item: PlaceRecommendation;
}

export default function RecommendationCard({ item }: Props) {
    return (
        <div style={{
            border: "1px solid #ddd",
            borderRadius: 8,
            padding: 16,
            marginBottom: 12,
            background: "#fafafa"
        }}>
            <h4 style={{ margin: "0 0 8px 0" }}>{item.name}</h4>
            <p style={{ margin: 0, color: "#555" }}>{item.reason}</p>
        </div>
    );
}