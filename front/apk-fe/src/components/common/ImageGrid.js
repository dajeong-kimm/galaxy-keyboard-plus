export default function ImageGrid() {
  return (
    <div className="grid grid-cols-3 gap-4 w-[300px] h-[300px] bg-gray-100 rounded-xl p-4">
      {[...Array(9)].map((_, i) => (
        <div
          key={i}
          className="w-full h-24 transition bg-gray-300 rounded-lg hover:scale-105"
        />
      ))}
    </div>
  );
}
