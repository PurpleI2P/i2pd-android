for file in configs/*1_78_0*
do
        echo "~~FILE~~"
        echo $file
        newname=```echo $file | sed "s/ndk23/ndk19/"```
        echo "cp $file to $newname"
        cp "$file" "$newname"
done
