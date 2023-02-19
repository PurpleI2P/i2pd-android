for lib_dir in */lib/
do
        #echo $lib_dir
        pushd `pwd`/$lib_dir
        ls
        for file in *
        do
                echo "~~~~FILE~~~~"
                echo $file
                tmp=(```echo $file | sed "s/-/ /"```)
                newFileName=${tmp[0]}.a
                echo "we will copy $file to $newFileName"
                cp $file $newFileName
        done
        popd
done

for include_dir in */include
do
        #echo $lib_dir
        pushd `pwd`/$include_dir
        ls
        for file in *
        do
                mv $file/boost boost
        done
        popd
done
