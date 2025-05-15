import Image from "next/image";

export default function ImageGrid() {
  const imageList = Array.from({ length: 9 }, (_, i) => `/images/grid-images/${i + 1}.jpg`);

  return (
    <div className="grid w-auto h-auto grid-cols-3 gap-4 p-4 bg-gray-100 rounded-xl">
      {imageList.map((src, i) => (
        <div
          key={i}
          className="w-32 h-32 overflow-hidden transition bg-gray-300 rounded-lg hover:scale-150"
        >
          <Image
            src={src}
            alt={`grid-${i}`}
            width={96}
            height={96}
            className="object-cover w-full h-full"
          />
        </div>
      ))}
    </div>
  );
}
