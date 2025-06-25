package com.example.youtubeautomaticuploader.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.youtubeautomaticuploader.databinding.FragmentGalleryBinding
import com.example.youtubeautomaticuploader.service.FileManagerService
import kotlinx.coroutines.*
import android.widget.TextView
import android.view.ViewParent
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private lateinit var galleryViewModel: GalleryViewModel
    private lateinit var fileManagerService: FileManagerService
    private lateinit var fileAdapter: FileAdapter

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        galleryViewModel = ViewModelProvider(this)[GalleryViewModel::class.java]
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)

        fileManagerService = FileManagerService(requireContext())
        setupRecyclerView()

        return binding.root
    }

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter()
        binding.recyclerViewFiles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fileAdapter
        }

        // Load files when fragment is created
        loadFiles()
    }

    private fun loadFiles() {
        scope.launch {
            try {
                // You can customize these paths or load from settings
                val videoDirectory = "/storage/emulated/0/Download"
                val subtitleDirectory = "/storage/emulated/0/Download"

                val videosWithSubtitles = withContext(Dispatchers.IO) {
                    fileManagerService.scanForVideosAndSubtitles(videoDirectory, subtitleDirectory)
                }

                fileAdapter.updateFiles(videosWithSubtitles)

            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }

    // RecyclerView Adapter for files
    private class FileAdapter : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

        private var files = listOf<FileManagerService.VideoWithSubtitle>()

        fun updateFiles(newFiles: List<FileManagerService.VideoWithSubtitle>) {
            files = newFiles
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return FileViewHolder(view)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            val videoWithSubtitle = files[position]
            holder.bind(videoWithSubtitle)
        }

        override fun getItemCount(): Int = files.size

        class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleText: TextView = itemView.findViewById(android.R.id.text1)
            private val subtitleText: TextView = itemView.findViewById(android.R.id.text2)

            fun bind(videoWithSubtitle: FileManagerService.VideoWithSubtitle) {
                val videoFile = videoWithSubtitle.videoFile
                val subtitleFile = videoWithSubtitle.subtitleFile

                titleText.text = videoFile.name

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val lastModified = Date(videoFile.lastModified())
                val sizeInMB = videoFile.length() / (1024.0 * 1024.0)

                val subtitleInfo = if (subtitleFile != null) {
                    " • With subtitle (${subtitleFile.name})"
                } else {
                    " • No subtitle"
                }

                subtitleText.text = "${String.format("%.1f", sizeInMB)} MB • ${dateFormat.format(lastModified)}$subtitleInfo"
            }
        }
    }
}
